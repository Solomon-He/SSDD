package org.ssdd.model;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/** Streaming inspection of ONNX's protobuf wire schema; raw weights are skipped, never materialized. */
@Component
public class OnnxModelGuard {
    public static final String CONTRACT = "ssdd-v2-2026-11-03";
    private static final Set<String> BUNDLED = Set.of(
            "76f41dd78b7fb8598ce2c4591cce6598fb51c9144cf1ed55aad793f972ddeeeb",
            "1a249fce31ccd51ae9af7b50623ba09cfcc83e078c5cd9671d325b8776680f16");

    public String inspect(Path path, String sha256) {
        try (var input = Files.newInputStream(path)) {
            var scanner = new Scanner(CodedInputStream.newInstance(input));
            scanner.model();
            if (!BUNDLED.contains(sha256) && !CONTRACT.equals(scanner.metadata.get("ssdd.contract")))
                throw ModelErrors.bad("未知来源模型需在 ONNX metadata_props 中声明 ssdd.contract=" + CONTRACT);
            return BUNDLED.contains(sha256) ? "bundled_sha256" : "onnx_metadata";
        } catch (IOException | IllegalArgumentException ex) {
            throw ModelErrors.bad("ONNX 文件结构无效或超过结构校验限制");
        }
    }

    @FunctionalInterface private interface MessageReader { void read() throws IOException; }
    private record TensorType(String name, int elementType, long[] shape) {}
    private record Node(String operation, List<String> inputs) {}

    private static final class Scanner {
        final CodedInputStream in;
        final Map<String,String> metadata = new HashMap<>();
        int depth, nodeCount, stringBytes;
        Scanner(CodedInputStream input) { in=input; in.setSizeLimit(500*1024*1024); }

        void model() throws IOException {
            int graphs=0;
            for (int tag; (tag=in.readTag())!=0;) {
                switch (WireFormat.getTagFieldNumber(tag)) {
                    case 7 -> { requireWire(tag,2); message(() -> graph(true)); graphs++; }
                    case 14 -> { requireWire(tag,2); message(this::metadataEntry); }
                    case 20,25 -> throw ModelErrors.bad("仅接受独立推理图，不接受训练图或本地函数");
                    default -> skip(tag);
                }
            }
            if (graphs!=1) throw ModelErrors.bad("ONNX 必须包含一个推理图");
        }

        void graph(boolean main) throws IOException {
            var inputs=new ArrayList<TensorType>(); var outputs=new ArrayList<TensorType>();
            var producers=new HashMap<String,Node>();
            for (int tag; (tag=in.readTag())!=0;) {
                switch (WireFormat.getTagFieldNumber(tag)) {
                    case 1 -> { requireWire(tag,2); message(() -> node(producers)); }
                    case 5 -> { requireWire(tag,2); message(this::tensor); }
                    case 15 -> { requireWire(tag,2); message(this::sparse); }
                    case 11 -> { requireWire(tag,2); message(() -> inputs.add(valueInfo())); }
                    case 12 -> { requireWire(tag,2); message(() -> outputs.add(valueInfo())); }
                    default -> skip(tag);
                }
            }
            if (!main) return;
            if (inputs.size()!=1 || outputs.size()!=1) throw ModelErrors.bad("模型必须只有一个图输入和一个图输出");
            signature(inputs.get(0),"input",3); signature(outputs.get(0),"output",4);
            Node tail=producers.get("output");
            var visited=new HashSet<Node>();
            while (tail!=null && visited.add(tail) && Set.of("Identity","Cast","Reshape","Transpose","Squeeze","Unsqueeze","Resize").contains(tail.operation()) && !tail.inputs().isEmpty())
                tail=producers.get(tail.inputs().get(0));
            if (tail!=null && Set.of("Sigmoid","Softmax","LogSoftmax").contains(tail.operation()))
                throw ModelErrors.bad("V2 输出必须为 logits，不能在输出端内置 sigmoid/softmax");
        }

        void node(Map<String,Node> producers) throws IOException {
            if (++nodeCount>50_000) throw new IOException("Node limit");
            String operation="", domain=""; var inputs=new ArrayList<String>(); var outputs=new ArrayList<String>();
            for (int tag; (tag=in.readTag())!=0;) {
                switch (WireFormat.getTagFieldNumber(tag)) {
                    case 1 -> { requireWire(tag,2); inputs.add(string()); }
                    case 2 -> { requireWire(tag,2); outputs.add(string()); }
                    case 4 -> { requireWire(tag,2); operation=string(); }
                    case 7 -> { requireWire(tag,2); domain=string(); }
                    case 5 -> { requireWire(tag,2); message(this::attribute); }
                    default -> skip(tag);
                }
            }
            if (!domain.isEmpty() && !domain.equals("ai.onnx")) throw ModelErrors.bad("不接受自定义算子域");
            var node=new Node(operation,List.copyOf(inputs));
            for (String output:outputs) if (!output.isEmpty() && producers.put(output,node)!=null) throw new IOException("Duplicate node output");
        }

        void attribute() throws IOException {
            for (int tag; (tag=in.readTag())!=0;) {
                switch (WireFormat.getTagFieldNumber(tag)) {
                    case 5,10 -> { requireWire(tag,2); message(this::tensor); }
                    case 6,11 -> { requireWire(tag,2); message(() -> graph(false)); }
                    case 22,23 -> { requireWire(tag,2); message(this::sparse); }
                    default -> skip(tag);
                }
            }
        }

        void tensor() throws IOException {
            for (int tag; (tag=in.readTag())!=0;) {
                switch (WireFormat.getTagFieldNumber(tag)) {
                    case 13 -> throw ModelErrors.bad("模型必须内嵌全部权重，不接受 external_data 引用");
                    case 14 -> { requireWire(tag,0); if (in.readInt32()!=0) throw ModelErrors.bad("模型必须内嵌全部权重"); }
                    default -> skip(tag); // Includes raw_data: CodedInputStream skips the payload without retaining it.
                }
            }
        }

        void sparse() throws IOException {
            for (int tag; (tag=in.readTag())!=0;) {
                if (Set.of(1,2).contains(WireFormat.getTagFieldNumber(tag))) { requireWire(tag,2); message(this::tensor); }
                else skip(tag);
            }
        }

        TensorType valueInfo() throws IOException {
            String name=""; int[] element={0}; var dimensions=new ArrayList<Long>();
            for (int tag; (tag=in.readTag())!=0;) {
                if (WireFormat.getTagFieldNumber(tag)==1) { requireWire(tag,2); name=string(); }
                else if (WireFormat.getTagFieldNumber(tag)==2) { requireWire(tag,2); message(() -> type(element,dimensions)); }
                else skip(tag);
            }
            return new TensorType(name,element[0],dimensions.stream().mapToLong(v->v).toArray());
        }

        void type(int[] element,List<Long> dimensions) throws IOException {
            for (int tag; (tag=in.readTag())!=0;) {
                if (WireFormat.getTagFieldNumber(tag)==1) {
                    requireWire(tag,2); message(() -> {
                        for (int inner; (inner=in.readTag())!=0;) {
                            if (WireFormat.getTagFieldNumber(inner)==1) { requireWire(inner,0); element[0]=in.readInt32(); }
                            else if (WireFormat.getTagFieldNumber(inner)==2) { requireWire(inner,2); message(() -> shape(dimensions)); }
                            else skip(inner);
                        }
                    });
                } else skip(tag);
            }
        }

        void shape(List<Long> dimensions) throws IOException {
            for (int tag; (tag=in.readTag())!=0;) {
                if (WireFormat.getTagFieldNumber(tag)==1) {
                    requireWire(tag,2); long[] value={-1};
                    message(() -> {
                        for (int inner; (inner=in.readTag())!=0;) {
                            if (WireFormat.getTagFieldNumber(inner)==1) { requireWire(inner,0); value[0]=in.readInt64(); }
                            else skip(inner);
                        }
                    });
                    dimensions.add(value[0]);
                    if (dimensions.size()>8) throw new IOException("Rank limit");
                } else skip(tag);
            }
        }

        void metadataEntry() throws IOException {
            String key="", value="";
            for (int tag; (tag=in.readTag())!=0;) {
                if (WireFormat.getTagFieldNumber(tag)==1) { requireWire(tag,2); key=string(); }
                else if (WireFormat.getTagFieldNumber(tag)==2) { requireWire(tag,2); value=string(); }
                else skip(tag);
            }
            if (metadata.put(key,value)!=null) throw new IOException("Duplicate metadata");
        }

        String string() throws IOException {
            int length=in.readRawVarint32();
            if (length<0 || length>16_384 || (stringBytes+=length)>8*1024*1024) throw new IOException("Metadata limit");
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(in.readRawBytes(length))).toString();
        }
        void message(MessageReader reader) throws IOException {
            if (++depth>32) throw new IOException("Recursion limit");
            int limit=in.pushLimit(in.readRawVarint32());
            try { reader.read(); if (in.getBytesUntilLimit()!=0) throw new IOException("Incomplete message"); }
            finally { in.popLimit(limit); depth--; }
        }
        void skip(int tag) throws IOException { if (!in.skipField(tag)) throw new IOException("Unexpected group"); }
        void requireWire(int tag,int expected) throws IOException { if (WireFormat.getTagWireType(tag)!=expected) throw new IOException("Wrong wire type"); }
        void signature(TensorType type,String name,int channels) {
            long[] shape=type.shape();
            if (!type.name().equals(name) || type.elementType()!=1 || shape.length!=4 || shape[1]!=channels
                    || (shape[0]!=-1&&shape[0]!=1) || (shape[2]!=-1&&shape[2]!=256) || (shape[3]!=-1&&shape[3]!=1600))
                throw ModelErrors.bad("模型必须声明 float32 NCHW input/output，输入三通道、输出四通道，兼容 256×1600");
        }
    }
}
