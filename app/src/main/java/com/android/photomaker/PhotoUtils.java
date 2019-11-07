package com.android.photomaker;

import android.content.Context;
import android.graphics.ImageFormat;
import android.media.ExifInterface;
import android.media.Image;
import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfRect;
import org.opencv.core.Range;
import org.opencv.core.Rect;
import android.util.Size;

import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.opencv.core.Core.flip;
import static org.opencv.core.Core.transpose;

public class PhotoUtils {

    private static final String TAG = "PhotoUtils";

    //face detection
    private static File mCascadeFile;
    private static CascadeClassifier mJavaDetector;

    public static void loadClassifier(Context context) {
        // load cascade file from application resources
        try {
            InputStream is = context.getResources().openRawResource(R.raw.haarcascade_frontalface_default);
            File cascadeDir = context.getDir("cascade", Context.MODE_PRIVATE);
            mCascadeFile = new File(cascadeDir, "haarcascade_frontalface_default.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);

            byte[] buffer = new byte[14096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
        } catch (IOException e) {
        }
    }

    public static Rect faceDetect(Mat mat_out_gray) {
        MatOfRect faces = new MatOfRect();
        Imgproc.blur(mat_out_gray,mat_out_gray, new org.opencv.core.Size(3,3));
        mJavaDetector.detectMultiScale(mat_out_gray, faces,1.1,5,4,
                new org.opencv.core.Size(30,30), new org.opencv.core.Size(mat_out_gray.rows(),mat_out_gray.rows()));
        Rect[] facesArray = faces.toArray();
        double max_size = 0;
        int max_index = 0;
        int i = 0;
        for ( Rect item : facesArray) {
            if (item.size().area() >= max_size) {
                max_index = i;
                max_size = item.size().area();
            }
            i++;
        }
        Log.d(TAG, "faces array " + facesArray.length);
        return  facesArray[max_index];
    }

    public static Rect[] faceDetect1(Mat mat_out_gray) {
        MatOfRect faces = new MatOfRect();
        Imgproc.blur(mat_out_gray,mat_out_gray, new org.opencv.core.Size(3,3));
        mJavaDetector.detectMultiScale(mat_out_gray, faces,1.1,6,4,
                new org.opencv.core.Size(30,30), new org.opencv.core.Size(mat_out_gray.rows(),mat_out_gray.rows()));
        Rect[] facesArray = faces.toArray();
        Log.d(TAG, "faces array " + facesArray.length);
        return  facesArray;
    }

    public static Mat photoResize(Mat mat_input, Rect face, Size s) {
        //Log.d(TAG, face: + face.x +   + face.y +   + face.width +   + face.height+ Size:  + s.getWidth()+   + s.getHeight());
        //Log.d(TAG, mat size: + mat_input.width() +   + mat_input.height());
        int start_x = face.x + face.width/2 - s.getWidth()/2;
        int start_y = face.y + 2*face.height/3 - s.getHeight()/2;
        start_x = start_x >= 0 ? start_x:0;
        start_y = start_y >= 0 ? start_y:0;

        int end_x = ((start_x+ s.getWidth()) >= mat_input.width()) ? mat_input.width():(start_x+ s.getWidth());
        int end_y = ((start_y+ s.getHeight()) >= mat_input.height()) ? mat_input.height():(start_y+ s.getHeight());
        Log.d(TAG,"sub mat:" + start_x +  + end_x+  + start_y+  + end_y);
        Mat img = mat_input.submat(start_y, end_y,start_x, end_x);
        return img;
    }

    /* face segmentation based on the background color, the background color should be white
     * The input is a RGB image mat, output is a binary mask*/
    public static Mat faceSegmentWithWhiteBg(Mat img) {
        //Mat mask = new Mat();
        Mat hsv = new Mat();
        Imgproc.cvtColor(img, hsv, Imgproc.COLOR_RGB2HSV);
        Scalar lower = new Scalar(0,0,120);
        Scalar higher = new Scalar(180,43,255);
        Mat mask = new Mat();
        org.opencv.core.Core.inRange(hsv, lower,higher,mask);
        Mat dilatedMask = new Mat();
        int size = mask.width();
        int kernelSize = 30>(size/10) ? (size/10): 50;
        Log.d(TAG, "size is " + size);
        Imgproc.erode(mask, mask, Imgproc.getStructuringElement(Imgproc.MORPH_CROSS, new org.opencv.core.Size( kernelSize,kernelSize)));
        Imgproc.dilate(mask, dilatedMask, Imgproc.getStructuringElement(Imgproc.MORPH_CROSS, new org.opencv.core.Size(kernelSize,kernelSize)));

        dilatedMask = MaskProc(dilatedMask);
        return dilatedMask;
    }

    //Filter out the small white areas
    public static Mat MaskProc(Mat mask) {
        Mat output = Mat.zeros(mask.rows(), mask.cols(), mask.type());
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat mHierarchy = new Mat();
        Imgproc.findContours(mask, contours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Find max contour area
        double maxArea = 0;
        Iterator<MatOfPoint> each = contours.iterator();
        while (each.hasNext()) {
            MatOfPoint wrapper = each.next();
            double area = Imgproc.contourArea(wrapper);
            if (area > maxArea)
                maxArea = area;
        }
        List<MatOfPoint> border = new ArrayList<MatOfPoint>();

        //Filter out the small connected domains
        each = contours.iterator();
        while (each.hasNext()) {
            MatOfPoint contour = each.next();
            if (Imgproc.contourArea(contour) >= 0.5*maxArea) {
                border.add(contour);
            }
        }
        Imgproc.fillPoly(output, border, new Scalar(255));

        return output;
    }

    public static Mat ImageProcWithMask(Mat img, Mat mask) {
        Log.d(TAG, "imag info: " + img.toString());
        Mat output = new Mat(img.rows(), img.cols(), img.type(), new Scalar(255,255,255,1));
        img.copyTo(output, mask);
        Imgproc.medianBlur(output, output, 3);

        return output;
    }

    public static Mat matRotate(Mat src, int Orientation) {
        if (src.empty()) {
            Log.d(TAG,"RotateMat src is empty!");
        }
        switch(Orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
            {
                //90
                transpose(src, src);
                flip(src, src, 1);
                break;
            }
            case ExifInterface.ORIENTATION_ROTATE_180:
            {
                flip(src, src, 1);
                flip(src, src, 0);
                break;
            }
            case ExifInterface.ORIENTATION_ROTATE_270:
            {
                transpose(src, src);
                flip(src, src, 0);
                break;
            }
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
            case ExifInterface.ORIENTATION_TRANSPOSE:
            case ExifInterface.ORIENTATION_TRANSVERSE:
            {
                break;
            }
        }
        return src;
    }

    /**
     * Takes an Android Image in the YUV_420_888 format and returns an OpenCV Mat.
     *
     * @param image Image in the YUV_420_888 format.
     * @return OpenCV Mat.
     */
    public static Mat imageToMat(Image image) {
        ByteBuffer buffer;
        int rowStride;
        int pixelStride;
        int width = image.getWidth();
        int height = image.getHeight();
        int offset = 0;

        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        for (int i = 0; i < planes.length; i++) {
            buffer = planes[i].getBuffer();
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            int w = (i == 0) ? width : width / 2;
            int h = (i == 0) ? height : height / 2;
            for (int row = 0; row < h; row++) {
                int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
                if (pixelStride == bytesPerPixel) {
                    int length = w * bytesPerPixel;
                    buffer.get(data, offset, length);

                    // Advance buffer the remainder of the row stride, unless on the last row.
                    // Otherwise, this will throw an IllegalArgumentException because the buffer
                    // doesn't include the last padding.
                    if (h - row != 1) {
                        buffer.position(buffer.position() + rowStride - length);
                    }
                    offset += length;
                } else {

                    // On the last row only read the width of the image minus the pixel stride
                    // plus one. Otherwise, this will throw a BufferUnderflowException because the
                    // buffer doesn't include the last padding.
                    if (h - row == 1) {
                        buffer.get(rowData, 0, width - pixelStride + 1);
                    } else {
                        buffer.get(rowData, 0, rowStride);
                    }

                    for (int col = 0; col < w; col++) {
                        data[offset++] = rowData[col * pixelStride];
                    }
                }
            }
        }
        // Finally, create the Mat.
        Mat mat = new Mat(height + height / 2, width, CvType.CV_8UC1);
        mat.put(0, 0, data);
        return mat;
    }
}
