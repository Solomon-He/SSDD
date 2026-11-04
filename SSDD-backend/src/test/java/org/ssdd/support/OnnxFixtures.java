package org.ssdd.support;

import com.google.protobuf.CodedOutputStream;
import org.ssdd.model.OnnxModelGuard;
import java.io.*;
import java.util.*;

/** Small real ONNX graphs with embedded normalization and a 1x1 convolution. */
public final class OnnxFixtures {
    private OnnxFixtures() {}
    @FunctionalInterface interface Writer { void write(CodedOutputStream output) throws IOException; }
    static byte[] message(Writer writer) {
        try { var bytes=new ByteArrayOutputStream();var out=CodedOutputStream.newInstance(bytes);writer.write(out);out.flush();return bytes.toByteArray(); }
        catch(IOException ex) { throw new UncheckedIOException(ex); }
    }
    static void nested(CodedOutputStream out,int field,byte[] value) throws IOException { out.writeByteArray(field,value); }
    static byte[] tensor(String name,long[] shape,float[] values,boolean external) {
        return message(out->{
            for(long dimension:shape)out.writeInt64(1,dimension);
            out.writeInt32(2,1);out.writeString(8,name);
            if(external) { nested(out,13,message(e->{e.writeString(1,"location");e.writeString(2,"../../application-local.yml");}));out.writeInt32(14,1); }
            else for(float value:values)out.writeFloat(4,value);
        });
    }
    static byte[] node(String operation,String[] inputs,String output,String domain,byte[] tensorAttribute) {
        return message(out->{
            for(String input:inputs)out.writeString(1,input);out.writeString(2,output);out.writeString(4,operation);
            if(domain!=null)out.writeString(7,domain);
            if(tensorAttribute!=null)nested(out,5,message(a->{a.writeString(1,"value");nested(a,5,tensorAttribute);}));
        });
    }
    static byte[] value(String name,int channels) {
        return message(out->{out.writeString(1,name);nested(out,2,message(type->nested(type,1,message(tensor->{
            tensor.writeInt32(1,1);nested(tensor,2,message(shape->{
                for(long dimension:new long[]{1,channels,256,1600})nested(shape,1,message(d->d.writeInt64(1,dimension)));
            }));
        }))));});
    }
    public static byte[] valid() { return model(4,false,false,false,false,false); }
    public static byte[] wrongChannels() { return model(5,false,false,false,false,false); }
    public static byte[] external() { return model(4,true,false,false,false,false); }
    public static byte[] sigmoidOutput() { return model(4,false,true,false,false,false); }
    public static byte[] customDomain() { return model(4,false,false,true,false,false); }
    public static byte[] nonfinite() { return model(4,false,false,false,true,false); }
    public static byte[] missingContract() { return model(4,false,false,false,false,true); }
    public static byte[] nestedExternal() {
        return message(out->{
            out.writeInt64(1,10);
            nested(out,7,message(graph->{
                nested(graph,1,node("Constant",new String[]{},"output",null,tensor("evil",new long[]{1},new float[]{1},true)));
                nested(graph,11,value("input",3));nested(graph,12,value("output",4));
            }));
        });
    }
    private static byte[] model(int channels,boolean external,boolean sigmoid,boolean custom,boolean nonfinite,boolean missingContract) {
        return message(out->{
            out.writeInt64(1,10);out.writeString(2,"ssdd-test-fixture");
            nested(out,8,message(opset->opset.writeInt64(2,13)));
            if(!missingContract)nested(out,14,message(entry->{entry.writeString(1,"ssdd.contract");entry.writeString(2,OnnxModelGuard.CONTRACT);}));
            nested(out,7,message(graph->{
                graph.writeString(2,"test-v2");
                nested(graph,1,node("Div",new String[]{"input","scale"},"scaled",null,null));
                nested(graph,1,node("Sub",new String[]{"scaled","mean"},"centered",null,null));
                nested(graph,1,node("Div",new String[]{"centered","std"},"normalized",null,null));
                nested(graph,1,node("Conv",new String[]{"normalized","weights","bias"},sigmoid?"logits":"output",custom?"untrusted":null,null));
                if(sigmoid)nested(graph,1,node("Sigmoid",new String[]{"logits"},"output",null,null));
                nested(graph,5,tensor("scale",new long[]{},new float[]{255},false));
                nested(graph,5,tensor("mean",new long[]{},new float[]{.3452f},false));
                nested(graph,5,tensor("std",new long[]{},new float[]{.2053f},false));
                float[] weights=new float[channels*3];Arrays.fill(weights,.01f);
                nested(graph,5,tensor("weights",new long[]{channels,3,1,1},weights,external));
                float[] bias=new float[channels];if(nonfinite)bias[0]=Float.NaN;
                nested(graph,5,tensor("bias",new long[]{channels},bias,false));
                nested(graph,11,value("input",3));nested(graph,12,value("output",channels));
            }));
        });
    }
}
