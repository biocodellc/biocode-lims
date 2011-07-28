package com.biomatters.plugins.biocode.labbench.plates;

import java.awt.*;
import java.awt.image.*;

/**
 * @author Steve
 * @version $Id: 27/08/2010 6:08:05 AM steve $
 */
public class GelScorer {
    @SuppressWarnings({"UnusedDeclaration"})
    private static ImageObserver nullImageObserver = new ImageObserver(){
        public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
            return false;
        }
    };


    public static boolean wellPasses(BufferedImage wellImage, int threshold) {
        int variance = getVariance(wellImage);
        System.out.print(variance);
        boolean passes = variance > threshold;
        System.out.println(passes ? "" : " FAIL");
        return passes;
    }

    private static int getVariance(BufferedImage image) {
        Raster raster = image.getData();
        long variance = 0;
        for(int x = 0; x < image.getWidth(); x++) {
            for(int y=2; y < image.getHeight()-2; y++) {
                variance += getVariance(raster, image.getWidth(), image.getHeight(), x,y);
            }
        }
        return (int)(variance/(image.getWidth()*image.getHeight()));
    }

    private static int getVariance(Raster raster, int width, int height, int x, int y) {
        int centerPixel = getPixel(raster, width, height, x, y);
        int maxVariance = 0;
        for(int y2 = y-2; y2 <= height-2; y2++) {
            int pixel = getPixel(raster, width, height, x, y2);
            maxVariance = Math.max(maxVariance, getVariance(centerPixel, pixel));
        }
        return maxVariance;
    }

    private static int getVariance(int pixel1, int pixel2) {
        int b1 = getBrightness(pixel1);
        int b2 = getBrightness(pixel2);
        return (b1-b2)*(b1-b2);
    }

    private static int getBrightness(int pixel) {
        return pixel;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    private static int getPixel(Raster raster, int width, int height, int x, int y) {
        int[] values = raster.getPixel(x,y,(int[])null);
        return (values[0]+values[1]+values[2])/3;
    }


}
