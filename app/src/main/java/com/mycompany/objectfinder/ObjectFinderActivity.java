package com.mycompany.objectfinder;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ObjectFinderActivity extends Activity implements CvCameraViewListener2 {
    private static final String  TAG                 = "FE_Camera";

    public static final int      FE_ORB      = 0;
    public static final int      FE_FAST      = 1;
    public static final int      FE_BRISK     = 2;
    public static final int      FE_GFTT     = 3;
    public static final int      FE_HARRIS     = 4;

    private MenuItem             mItemPreviewORB;
    private MenuItem             mItemPreviewFAST;
    private MenuItem             mItemPreviewBRISK;
    private MenuItem             mItemPreviewGFTT;
    private MenuItem             mItemPreviewHARRIS;
    private CameraBridgeViewBase mOpenCvCameraView;


    private Mat                  mIntermediateMat;
    private Mat gray = null;
    private Mat rgb = null;
    private FeatureDetector featuredetector;

    private DescriptorMatcher descriptorMatcher;
    private DescriptorExtractor descriptorExtractor;
    private Mat template;
    private Mat templateDescriptors;
    private MatOfKeyPoint templateKeypoints;
    private int multiplier = 2;
    private boolean bIsTemplateLoaded = false;
    private boolean bFrameReady = false;

    public static int           viewMode = FE_ORB;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    loadTemplate();
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public ObjectFinderActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(com.mycompany.objectfinder.R.layout.activity_feature_extraction);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.object_finder_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);
        //mOpenCvCameraView.setMaxFrameSize(960,540);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        mItemPreviewORB  = menu.add("ORB");
        mItemPreviewFAST  = menu.add("FAST");
        mItemPreviewBRISK = menu.add("BRISK");
        mItemPreviewGFTT = menu.add("GFTT");
        mItemPreviewHARRIS = menu.add("HARRIS");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
        /*
        if (item == mItemPreviewORB)
            viewMode = FE_ORB;
        if (item == mItemPreviewFAST)
            viewMode = FE_FAST;
        else if (item == mItemPreviewBRISK)
            viewMode = FE_BRISK;
        else if (item == mItemPreviewGFTT)
            viewMode = FE_GFTT;
        else if (item == mItemPreviewHARRIS)
            viewMode = FE_HARRIS;
        */
        return true;
    }

    public void onCameraViewStarted(int width, int height) {

    }

    public void onCameraViewStopped() {
        // Explicitly deallocate Mats
        if (mIntermediateMat != null)
            mIntermediateMat.release();

        mIntermediateMat = null;
    }

    private void loadTemplate(){

        Bitmap bmpTemp = BitmapFactory.decodeResource(getResources(), R.drawable.gum).copy(Bitmap.Config.ARGB_8888, true);
        template = new Mat(bmpTemp.getHeight(), bmpTemp.getWidth(), CvType.CV_8U);
        Utils.bitmapToMat(bmpTemp, template);
        templateKeypoints = new MatOfKeyPoint();
        featuredetector = FeatureDetector.create(FeatureDetector.ORB);
        featuredetector.detect(template, templateKeypoints);
        templateDescriptors = new Mat();
        descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
        descriptorExtractor.compute(template, templateKeypoints, templateDescriptors);
        bIsTemplateLoaded = true;
        bFrameReady = true;
    }

    private List<DMatch> filterMatches(MatOfDMatch allMatches) {

        List<DMatch> allMatchesList = allMatches.toList();
        double maxDist = 0; double minDist = 9999;

        for(int i = 0; i < allMatchesList.size(); i++) {
            double dist = allMatchesList.get(i).distance;
            if(dist < minDist)  minDist = dist;
            if(dist > maxDist)  maxDist = dist;
        }

        double threshold = multiplier * minDist;
        List<DMatch> goodMatchesList = new ArrayList<>();
        for(int i = 0; i < allMatchesList.size(); i++) {
            if(allMatchesList.get(i).distance < threshold)
                goodMatchesList.add(allMatchesList.get(i));
        }

        //MatOfDMatch goodMatches = new MatOfDMatch();
        //goodMatches.fromList(goodMatchesList);
        //return goodMatches;
        return goodMatchesList;
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        //if (gray != null)
        //    gray.release();
        gray = inputFrame.gray();
        rgb = inputFrame.rgba();
        Imgproc.cvtColor(rgb, rgb, Imgproc.COLOR_RGBA2RGB);
/*
        switch(viewMode) {
            case FE_ORB:
                featuredetector = FeatureDetector.create(FeatureDetector.ORB);
                break;
            case FE_FAST:
                featuredetector = FeatureDetector.create(FeatureDetector.FAST);
                break;
            case FE_BRISK:
                featuredetector = FeatureDetector.create(FeatureDetector.BRISK);
                break;
            case FE_GFTT:
                featuredetector = FeatureDetector.create(FeatureDetector.GFTT);
                break;
            case FE_HARRIS:
                featuredetector = FeatureDetector.create(FeatureDetector.HARRIS);
                break;
        }*/
        featuredetector = FeatureDetector.create(FeatureDetector.ORB);
        descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
        descriptorMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);

        MatOfKeyPoint keypoints = new MatOfKeyPoint();
        featuredetector.detect(gray, keypoints);
        if(keypoints.rows() < 4)
            return rgb;
        Features2d.drawKeypoints(rgb, keypoints, rgb, new Scalar(0, 255, 255), Features2d.NOT_DRAW_SINGLE_POINTS);

        Mat descriptors = new Mat();
        descriptorExtractor.compute(gray, keypoints, descriptors);
        MatOfDMatch matches = new MatOfDMatch();
        descriptorMatcher.match(descriptors, templateDescriptors, matches);

        //MatOfDMatch goodMatches = new MatOfDMatch();
        List<DMatch> goodMatchesList = new ArrayList<>();
        goodMatchesList = filterMatches(matches);

        List<KeyPoint> objKpList = new ArrayList<>();
        List<KeyPoint> sceneKpList = new ArrayList<>();
        objKpList = templateKeypoints.toList();
        sceneKpList = keypoints.toList();
        LinkedList<Point> objList = new LinkedList<>();
        LinkedList<Point> sceneList = new LinkedList<>();
        for(int i = 0; i < goodMatchesList.size(); i++) {
            objList.addLast(objKpList.get(goodMatchesList.get(i).trainIdx).pt);
            sceneList.addLast(sceneKpList.get(goodMatchesList.get(i).queryIdx).pt);
        }

        String result_str = "Total Features: " + keypoints.rows() + "   Total Matches: " + matches.rows() + "   Good Matches: " + goodMatchesList.size();
        Imgproc.putText(rgb,result_str, new Point(30, rgb.rows() - 30), Core.FONT_HERSHEY_COMPLEX, 1.0,new Scalar(255, 255, 0), 1, 8, false);

        MatOfPoint2f obj = new MatOfPoint2f();
        MatOfPoint2f scene = new MatOfPoint2f();

        obj.fromList(objList);
        scene.fromList(sceneList);

        Mat mask = new Mat();
        Mat H = Calib3d.findHomography(obj, scene, Calib3d.RANSAC, 3, mask, 2000, 0.995);
        Mat objCorners = new Mat(4, 1, CvType.CV_32FC2);
        Mat sceneCorners = new Mat(4, 1, CvType.CV_32FC2);

        objCorners.put(0, 0, new double[]{0, 0});
        objCorners.put(1, 0, new double[]{template.cols(), 0});
        objCorners.put(2, 0, new double[]{template.cols(), template.rows()});
        objCorners.put(3, 0, new double[]{0, template.rows()});

        try {
            Core.perspectiveTransform(objCorners, sceneCorners, H);
        }
        catch(CvException e){
            e.printStackTrace();
            Log.e(TAG, "perspectiveTransform returned an assertion failed error.");
            return rgb;
        }

        Imgproc.line(rgb, new Point(sceneCorners.get(0, 0)), new Point(sceneCorners.get(1, 0)), new Scalar(255, 0, 0),4);
        Imgproc.line(rgb, new Point(sceneCorners.get(1, 0)), new Point(sceneCorners.get(2, 0)), new Scalar(255, 0, 0),4);
        Imgproc.line(rgb, new Point(sceneCorners.get(2, 0)), new Point(sceneCorners.get(3, 0)), new Scalar(255, 0, 0),4);
        Imgproc.line(rgb, new Point(sceneCorners.get(3, 0)), new Point(sceneCorners.get(0, 0)), new Scalar(255, 0, 0), 4);

        keypoints.release();
        return rgb;
    }
}