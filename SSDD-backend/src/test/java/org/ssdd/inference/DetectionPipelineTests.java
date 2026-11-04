package org.ssdd.inference;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.ssdd.detection.*;
import org.ssdd.common.exception.BusinessException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class DetectionPipelineTests {
    static DetectionProperties config() {return new DetectionProperties(10485760,8000000,8192,false,10,Duration.ofMinutes(1),Duration.ofMinutes(15),Duration.ofSeconds(30));}
    private final DetectionImageDecoder decoder=new DetectionImageDecoder(config());
    private final V2DetectionPostprocessor processor=new V2DetectionPostprocessor();
    public static byte[] png(int w,int h) throws IOException {
        var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)image.setRGB(x,y,((x%256)<<16)|((y%256)<<8)|41);
        return encode(image,"png");
    }
    static byte[] encode(BufferedImage image,String format) throws IOException {var output=new ByteArrayOutputStream();assertTrue(ImageIO.write(image,format,output));return output.toByteArray();}
    @Test void decodesActualFormatsRegardlessOfFilenameAndRejectsOthers() throws Exception {
        for(String format:List.of("jpeg","png","bmp")) {
            byte[] bytes=encode(new BufferedImage(13,7,BufferedImage.TYPE_INT_RGB),format);
            var upload=decoder.read(new MockMultipartFile("image","../../wrong.exe","text/plain",bytes));
            assertEquals(64,upload.sha256().length());var image=decoder.decode(upload.bytes());assertEquals(13,image.rgb().getWidth());assertEquals(7,image.rgb().getHeight());
        }
        byte[] gif=encode(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"gif");
        assertThrows(BusinessException.class,()->decoder.decode(gif));assertThrows(BusinessException.class,()->decoder.decode(new byte[]{1,2,3}));
        assertThrows(BusinessException.class,()->decoder.read(new MockMultipartFile("image",new byte[0])));
        assertThrows(BusinessException.class,()->decoder.read(new MockMultipartFile("image",new byte[10485761])));
        assertThrows(BusinessException.class,()->decoder.decode(png(8193,1)));
        var limited=new DetectionImageDecoder(new DetectionProperties(1000,100,100,false,10,Duration.ofMinutes(1),Duration.ofMinutes(15),Duration.ofSeconds(30)));
        assertThrows(BusinessException.class,()->limited.decode(png(11,10)));
    }
    @Test void flattensAlphaOnWhiteAndKeepsFloatRgbNchwWithoutNormalization() throws Exception {
        var source=new BufferedImage(2,1,BufferedImage.TYPE_INT_ARGB);source.setRGB(0,0,0x00ff0000);source.setRGB(1,0,0xff0064c8);
        var image=decoder.decode(encode(source,"png")).rgb();assertEquals(0xffffff,image.getRGB(0,0)&0xffffff);
        float[] tensor=decoder.tensor(image);int size=1600*256;
        assertEquals(255,tensor[0]);assertEquals(255,tensor[size]);assertEquals(255,tensor[size*2]);
        assertEquals(0,tensor[1599]);assertEquals(100,tensor[size+1599]);assertEquals(200,tensor[2*size+1599]);
        // x=800 -> source x=.500625; float bilinear, never rounded back to uint8.
        assertEquals(255*(1-.500625),tensor[800],.0001);
    }
    @Test void handlesAllEightJpegExifOrientationsAndBothTiffByteOrders() throws Exception {
        var source=new BufferedImage(3,2,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<2;y++)for(int x=0;x<3;x++)source.setRGB(x,y,(x*90<<16)|(y*170<<8)|50);
        byte[] jpeg=encode(source,"jpeg");var base=decoder.decode(jpeg).rgb();
        int[][] expected={ {0,1,2,3,4,5},{2,1,0,5,4,3},{5,4,3,2,1,0},{3,4,5,0,1,2},
                {0,3,1,4,2,5},{3,0,4,1,5,2},{5,2,4,1,3,0},{2,5,1,4,0,3} };
        for(int orientation=1;orientation<=8;orientation++)for(var order:List.of(ByteOrder.LITTLE_ENDIAN,ByteOrder.BIG_ENDIAN)) {
            var decoded=decoder.decode(exif(jpeg,orientation,order));assertEquals(orientation,decoded.orientation());
            int w=orientation>=5?2:3;assertEquals(w,decoded.rgb().getWidth());assertEquals(6/w,decoded.rgb().getHeight());
            for(int p=0;p<6;p++){int old=expected[orientation-1][p];assertEquals(base.getRGB(old%3,old/3),decoded.rgb().getRGB(p%w,p/w));}
        }
        byte[] invalid=exif(jpeg,6,ByteOrder.LITTLE_ENDIAN);Arrays.fill(invalid,16,20,(byte)255);
        assertEquals(1,decoder.decode(invalid).orientation());
    }
    private byte[] exif(byte[] jpeg,int orientation,ByteOrder order) throws IOException {
        var tiff=ByteBuffer.allocate(26).order(order);tiff.put(order==ByteOrder.LITTLE_ENDIAN?(byte)'I':(byte)'M');tiff.put(tiff.get(0));
        tiff.putShort((short)42).putInt(8).putShort((short)1).putShort((short)0x112).putShort((short)3).putInt(1).putShort((short)orientation).putShort((short)0).putInt(0);
        var output=new ByteArrayOutputStream();output.write(jpeg,0,2);output.write(new byte[]{(byte)255,(byte)225,0,34,'E','x','i','f',0,0});output.write(tiff.array());output.write(jpeg,2,jpeg.length-2);return output.toByteArray();
    }
    @Test void appliesThresholdEightConnectivityComponentAndClassAreaInOrder() {
        int w=50,h=50,n=w*h;float[] logits=new float[4*n];Arrays.fill(logits,-1);
        // Diagonal pixels form one 8-connected component, size 2; threshold equality is positive.
        logits[0]=0;logits[w+1]=-0.0f;
        assertEquals(1,processor.filter(logits,w,h,2,new int[]{2,2,2,2}).components()[0]);
        assertEquals(0,processor.filter(logits,w,h,3,new int[]{2,2,2,2}).components()[0]);
        assertEquals(0,processor.filter(logits,w,h,2,new int[]{3,2,2,2}).components()[0]);
        // Two sub-150 regions must not combine to pass the class total threshold.
        Arrays.fill(logits,-1);for(int y=0;y<10;y++)for(int x=0;x<10;x++){logits[y*w+x]=1;logits[(y+20)*w+x+20]=1;}
        assertEquals(0,processor.filter(logits,w,h,150,new int[]{150,0,0,0}).components()[0]);
        logits[0]=Float.NaN;assertThrows(BusinessException.class,()->processor.filter(logits,w,h,1,new int[]{0,0,0,0}));
    }
    @Test void preservesAllLabelsAndUsesOriginalPixelsButModelSpaceRegionCounts() {
        int size=1600*256;float[] logits=new float[size*4];Arrays.fill(logits,-1);
        // Four fully overlapping classes, class 2 has larger probability; class 3 ties it.
        Arrays.fill(logits,0,size,0);Arrays.fill(logits,size,size*3,2);Arrays.fill(logits,size*3,size*4,1);
        var output=processor.process(logits,new BufferedImage(37,19,BufferedImage.TYPE_INT_RGB));
        assertArrayEquals(new long[]{703,703,703,703},output.pixels());assertEquals(703,output.unionPixels());assertEquals(4,output.count());
        assertEquals(2,output.images().get("mask").getRaster().getSample(0,0,0));
        for(int c=1;c<=4;c++)assertEquals(255,output.images().get("class-"+c).getRaster().getSample(0,0,0));
        Arrays.fill(logits,-1);output=processor.process(logits,new BufferedImage(17,3,BufferedImage.TYPE_INT_RGB));assertEquals(0,output.count());assertEquals(0,output.unionPixels());
        for(int y=0;y<20;y++)for(int x=0;x<30;x++){logits[y*1600+x]=1;logits[y*1600+x+1000]=1;}
        output=processor.process(logits,new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB));
        assertEquals(2,output.count());assertEquals(1,output.unionPixels());assertEquals(1,output.pixels()[0]);
    }
    @Test void filtersAtExactProductionAreaBoundaries() {
        int size=1600*256;int[] limits={600,600,900,2000};float[] values=new float[4*size];Arrays.fill(values,-1);
        for(int c=0;c<4;c++)Arrays.fill(values,c*size,c*size+limits[c],1);
        assertArrayEquals(new int[]{1,1,1,1},processor.filter(values,1600,256,150,limits).components());
        for(int c=0;c<4;c++)values[c*size+limits[c]-1]=-1;
        assertArrayEquals(new int[]{0,0,0,0},processor.filter(values,1600,256,150,limits).components());
    }
    @Test void resizeMatchesIndependentOpenCvFloat32Samples() throws Exception {
        var reference=new com.fasterxml.jackson.databind.ObjectMapper().readTree(getClass().getResourceAsStream("/inference/resize-reference.json"));
        for(var sample:reference.path("samples")) {
            int w=sample.path("source_width").asInt(),h=sample.path("source_height").asInt();var source=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)source.setRGB(x,y,(((x*37+y*11)%256)<<16)|(((x*13+y*29)%256)<<8)|((x*7+y*43)%256));
            var tensor=decoder.tensor(source);int p=sample.path("y").asInt()*1600+sample.path("x").asInt();
            for(int c=0;c<3;c++)assertEquals(sample.path("rgb").get(c).asDouble(),tensor[c*1600*256+p],reference.path("tolerance").asDouble());
        }
    }
}
