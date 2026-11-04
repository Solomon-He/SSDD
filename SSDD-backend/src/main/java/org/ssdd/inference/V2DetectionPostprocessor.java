package org.ssdd.inference;

import org.springframework.stereotype.Component;
import org.ssdd.detection.DetectionErrors;
import java.awt.image.BufferedImage;
import java.util.*;

@Component
public class V2DetectionPostprocessor {
    public record Masks(byte[][] channels,int[] components) {}
    public record Result(Map<String,BufferedImage> images,long[] pixels,int[] components,long unionPixels,long totalPixels) {
        public int count() { return Arrays.stream(components).sum(); }
    }
    private static final int[] MIN_AREA={600,600,900,2000};
    private static final int[] COLORS={0xef4444,0x10b981,0x3b82f6,0xf59e0b};
    public static final double ALPHA=.45;
    public Masks filter(float[] logits,int width,int height,int minComponent,int[] minArea) {
        int size=Math.multiplyExact(width,height);
        if(logits.length!=4*size||minArea.length!=4)throw DetectionErrors.bad("推理输出尺寸错误");
        byte[][] masks=new byte[4][size];int[] components=new int[4];int[] queue=new int[size];
        for(int c=0;c<4;c++) {
            // sigmoid(logit)>=0.5 is exactly logit>=0 for finite floats, including signed zero.
            for(int i=0;i<size;i++) {
                float value=logits[c*size+i];if(!Float.isFinite(value))throw new org.ssdd.common.exception.BusinessException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"模型输出包含非有限数值");
                masks[c][i]=(byte)(value>=0?1:0);
            }
            boolean[] visited=new boolean[size];int pixels=0;
            for(int start=0;start<size;start++) {
                if(masks[c][start]==0||visited[start])continue;
                int head=0,tail=1;queue[0]=start;visited[start]=true;
                while(head<tail) {
                    int p=queue[head++],x=p%width,y=p/width;
                    for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                        int nx=x+dx,ny=y+dy;
                        if(nx<0||nx>=width||ny<0||ny>=height)continue;
                        int next=ny*width+nx;
                        if(!visited[next]&&masks[c][next]!=0) {visited[next]=true;queue[tail++]=next;}
                    }
                }
                if(tail<minComponent)for(int i=0;i<tail;i++)masks[c][queue[i]]=0;
                else {pixels+=tail;components[c]++;}
            }
            if(pixels<minArea[c]) {Arrays.fill(masks[c],(byte)0);components[c]=0;}
        }
        return new Masks(masks,components);
    }
    public Result process(float[] logits,BufferedImage original) {
        int mw=DetectionImageDecoder.WIDTH,mh=DetectionImageDecoder.HEIGHT,size=mw*mh;
        var filtered=filter(logits,mw,mh,150,MIN_AREA);
        int w=original.getWidth(),h=original.getHeight();
        var images=new LinkedHashMap<String,BufferedImage>();images.put("original",original);
        var preview=new BufferedImage(w,h,BufferedImage.TYPE_BYTE_GRAY);
        var overlay=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        BufferedImage[] channels=new BufferedImage[4];
        for(int c=0;c<4;c++) {channels[c]=new BufferedImage(w,h,BufferedImage.TYPE_BYTE_GRAY);images.put("class-"+(c+1),channels[c]);}
        long[] pixels=new long[4];long union=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            // OpenCV INTER_NEAREST coordinate convention, shared by channels and preview.
            int p=(int)((long)y*mh/h)*mw+(int)((long)x*mw/w);
            int winner=-1;float best=Float.NEGATIVE_INFINITY;
            for(int c=0;c<4;c++)if(filtered.channels[c][p]!=0) {
                channels[c].getRaster().setSample(x,y,0,255);pixels[c]++;
                float value=logits[c*size+p];if(value>best) {winner=c;best=value;}
            }
            int source=original.getRGB(x,y);
            if(winner>=0) {
                union++;preview.getRaster().setSample(x,y,0,winner+1);
                int color=COLORS[winner],rgb=0;
                for(int shift=16;shift>=0;shift-=8)rgb|=(int)Math.round(((source>>shift)&255)*(1-ALPHA)+((color>>shift)&255)*ALPHA)<<shift;
                overlay.setRGB(x,y,rgb);
            } else overlay.setRGB(x,y,source);
        }
        images.put("mask",preview);images.put("overlay",overlay);
        return new Result(Collections.unmodifiableMap(images),pixels,filtered.components,union,(long)w*h);
    }
}
