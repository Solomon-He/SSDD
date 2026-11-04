package org.ssdd.inference;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.ssdd.detection.*;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.util.*;

/** Decode once, strip metadata, flatten transparency on white, and apply JPEG EXIF orientation. */
@Component
public class DetectionImageDecoder {
    public static final int WIDTH=1600, HEIGHT=256;
    public record Upload(byte[] bytes,String sha256) {}
    public record Image(BufferedImage rgb,int encodedWidth,int encodedHeight,int orientation,String format) {}
    private final DetectionProperties config;
    public DetectionImageDecoder(DetectionProperties config) { this.config=config; }
    public Upload read(MultipartFile file) {
        if(file==null||file.isEmpty())throw DetectionErrors.bad("请上传图片");
        if(file.getSize()>config.maxFileBytes())throw DetectionErrors.bad("检测图片不能超过配置大小上限");
        try(var input=file.getInputStream()) {
            byte[] bytes=input.readNBytes(Math.toIntExact(config.maxFileBytes()+1));
            if(bytes.length==0||bytes.length>config.maxFileBytes())throw DetectionErrors.bad("检测图片为空或超过大小上限");
            return new Upload(bytes,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch(IOException ex) { throw DetectionErrors.bad("图片读取失败"); }
        catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public Image decode(byte[] bytes) {
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw DetectionErrors.bad("仅支持实际可解码的 JPEG/PNG/BMP 图片");
            var reader=readers.next();
            try {
                reader.setInput(input,true,true);
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!Set.of("jpeg","jpg","png","bmp").contains(format))throw DetectionErrors.bad("仅支持 JPEG/PNG/BMP 图片");
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<=0||h<=0||w>config.maxDimension()||h>config.maxDimension()||(long)w*h>config.maxPixels())throw DetectionErrors.bad("图片尺寸或像素数超过上限");
                BufferedImage source=reader.read(0);
                if(source==null)throw DetectionErrors.bad("图片无法解码");
                int orientation=(format.equals("jpeg")||format.equals("jpg"))?jpegOrientation(bytes):1;
                int outW=orientation>=5?h:w,outH=orientation>=5?w:h;
                var rgb=new BufferedImage(outW,outH,BufferedImage.TYPE_INT_RGB);
                for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                    int pixel=source.getRGB(x,y),a=pixel>>>24;
                    int r=(((pixel>>16)&255)*a+255*(255-a)+127)/255;
                    int g=(((pixel>>8)&255)*a+255*(255-a)+127)/255;
                    int b=((pixel&255)*a+255*(255-a)+127)/255;
                    int dx=x,dy=y;
                    switch(orientation) {
                        case 2 -> dx=w-1-x;
                        case 3 -> {dx=w-1-x;dy=h-1-y;}
                        case 4 -> dy=h-1-y;
                        case 5 -> {dx=y;dy=x;}
                        case 6 -> {dx=h-1-y;dy=x;}
                        case 7 -> {dx=h-1-y;dy=w-1-x;}
                        case 8 -> {dx=y;dy=w-1-x;}
                        default -> { }
                    }
                    rgb.setRGB(dx,dy,(r<<16)|(g<<8)|b);
                }
                return new Image(rgb,w,h,orientation,format);
            } finally { reader.dispose(); }
        } catch(IOException|IndexOutOfBoundsException ex) { throw DetectionErrors.bad("图片无法解码"); }
    }
    /** Half-pixel bilinear sampling of RGB bytes; embedded model normalization is left untouched. */
    public float[] tensor(BufferedImage rgb) {
        float[] values=new float[3*WIDTH*HEIGHT];
        int w=rgb.getWidth(),h=rgb.getHeight(),size=WIDTH*HEIGHT;
        for(int y=0;y<HEIGHT;y++) {
            double sy=Math.max(0,Math.min(h-1,(y+.5)*h/HEIGHT-.5));int y0=(int)sy,y1=Math.min(y0+1,h-1);double fy=sy-y0;
            for(int x=0;x<WIDTH;x++) {
                double sx=Math.max(0,Math.min(w-1,(x+.5)*w/WIDTH-.5));int x0=(int)sx,x1=Math.min(x0+1,w-1);double fx=sx-x0;
                int p00=rgb.getRGB(x0,y0),p01=rgb.getRGB(x1,y0),p10=rgb.getRGB(x0,y1),p11=rgb.getRGB(x1,y1);
                for(int c=0;c<3;c++) {
                    int shift=16-c*8;
                    double top=((p00>>shift)&255)*(1-fx)+((p01>>shift)&255)*fx;
                    double bottom=((p10>>shift)&255)*(1-fx)+((p11>>shift)&255)*fx;
                    values[c*size+y*WIDTH+x]=(float)(top*(1-fy)+bottom*fy);
                }
            }
        }
        return values;
    }
    private static int jpegOrientation(byte[] bytes) {
        if(bytes.length<4||(bytes[0]&255)!=255||(bytes[1]&255)!=216)return 1;
        int p=2;
        while(p+4<=bytes.length) {
            if((bytes[p++]&255)!=255)break;
            int marker=bytes[p++]&255;
            while(marker==255&&p<bytes.length)marker=bytes[p++]&255;
            if(marker==218||marker==217)break;
            if(marker==1||(marker>=208&&marker<=215))continue;
            int length=((bytes[p]&255)<<8)|(bytes[p+1]&255);
            if(length<2||p+(long)length>bytes.length)break;
            if(marker==225&&length>=16&&bytes[p+2]=='E'&&bytes[p+3]=='x'&&bytes[p+4]=='i'&&bytes[p+5]=='f'&&bytes[p+6]==0&&bytes[p+7]==0) {
                int base=p+8,end=p+length;
                ByteOrder order=bytes[base]=='I'&&bytes[base+1]=='I'?ByteOrder.LITTLE_ENDIAN:ByteOrder.BIG_ENDIAN;
                if(!((bytes[base]=='I'&&bytes[base+1]=='I')||(bytes[base]=='M'&&bytes[base+1]=='M')))return 1;
                ByteBuffer data=ByteBuffer.wrap(bytes).order(order);
                if(Short.toUnsignedInt(data.getShort(base+2))!=42)return 1;
                long offset=Integer.toUnsignedLong(data.getInt(base+4));long pos=base+offset;
                if(pos<base+8||pos+2>end)return 1;
                int count=Short.toUnsignedInt(data.getShort((int)pos));
                for(int i=0;i<count;i++) {
                    long entry=pos+2L+12L*i;if(entry+12>end)return 1;int e=(int)entry;
                    if(Short.toUnsignedInt(data.getShort(e))==0x112&&data.getShort(e+2)==3&&data.getInt(e+4)==1) {
                        int value=Short.toUnsignedInt(data.getShort(e+8));return value>=1&&value<=8?value:1;
                    }
                }
            }
            p+=length;
        }
        return 1;
    }
}
