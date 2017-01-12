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
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

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
    private Vibrator vib;


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

    /*
    This function is called when the opencv camera is launched
     */
    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    //loadTemplate();
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

    /* Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(com.mycompany.objectfinder.R.layout.activity_feature_extraction);
        vib = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.object_finder_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {   // Called when user taps on screen
                grabFrame();
            }
        });
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

    public void onCameraViewStarted(int width, int height) {

    }

    public void onCameraViewStopped() {
        // Explicitly deallocate Mats
        if (mIntermediateMat != null)
            mIntermediateMat.release();

        mIntermediateMat = null;
    }

    /*
    Called when screen is tapped, grabs the latest frame taken from the camera and uses that as a
    template for future matches by extracting its features and descriptors.
     */
    public void grabFrame(){
        if(bFrameReady) {   //Only executes the function if there is an available frame
            vib.vibrate(250);
            template = gray;
            templateKeypoints = new MatOfKeyPoint();
            featuredetector = FeatureDetector.create(FeatureDetector.ORB);
            featuredetector.detect(template, templateKeypoints);
            if(templateKeypoints.rows() > 100) {
                Toast.makeText(ObjectFinderActivity.this,
                        "Matching tapped frame to new frames", Toast.LENGTH_LONG).show();
                templateDescriptors = new Mat();
                descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
                descriptorExtractor.compute(template, templateKeypoints, templateDescriptors);
                bIsTemplateLoaded = true;
            }
            else {  //If there is not enough features (<=100), it does not do matching
                Toast.makeText(ObjectFinderActivity.this,
                        "ERROR: Not enough features", Toast.LENGTH_LONG).show();
                bIsTemplateLoaded = false;
            }
        }
    }

    /*
    Filters out any matches with a distance that is greater than the threshold
    Threshold is determined by the minimum distance of the match list * the multiplier
     */
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
            if(allMatchesList.get(i).distance <= threshold)
                goodMatchesList.add(allMatchesList.get(i));
        }

        //MatOfDMatch goodMatches = new MatOfDMatch();
        //goodMatches.fromList(goodMatchesList);
        //return goodMatches;
        return goodMatchesList;
    }

    /*
    Called on every camera frame. Processes the current frame by extracting its features, matching
    the template features to it, then applying findHomography and perspectiveTransform to obtain the
    four corners of the match, which is drawn to the frame and displayed.
     */
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        gray = inputFrame.gray();
        rgb = inputFrame.rgba();
        Imgproc.cvtColor(rgb, rgb, Imgproc.COLOR_RGBA2RGB);
        if(bIsTemplateLoaded) { // Does not perform any computation unless a frame is tapped by the user

            featuredetector = FeatureDetector.create(FeatureDetector.ORB);
            descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
            descriptorMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);

            // Extract keypoints and draws them to the output frame
            MatOfKeyPoint keypoints = new MatOfKeyPoint();
            featuredetector.detect(gray, keypoints);
            if (keypoints.rows() < 4)   // Minimum of 4 features needed, return if criterion is not met
                return rgb;
            Features2d.drawKeypoints(rgb, keypoints, rgb, new Scalar(0, 255, 255), Features2d.NOT_DRAW_SINGLE_POINTS);

            // Extract descriptors and matches them to the template's descriptors
            Mat descriptors = new Mat();
            descriptorExtractor.compute(gray, keypoints, descriptors);
            MatOfDMatch matches = new MatOfDMatch();
            descriptorMatcher.match(descriptors, templateDescriptors, matches);

            // Filters matches
            List<DMatch> goodMatchesList = new ArrayList<>();
            goodMatchesList = filterMatches(matches);

            // Iterate through good matches and put the 2D points of the object (template) and frame (scene) into a list
            List<KeyPoint> objKpList = new ArrayList<>();
            List<KeyPoint> sceneKpList = new ArrayList<>();
            objKpList = templateKeypoints.toList();
            sceneKpList = keypoints.toList();
            LinkedList<Point> objList = new LinkedList<>();
            LinkedList<Point> sceneList = new LinkedList<>();
            for (int i = 0; i < goodMatchesList.size(); i++) {
                objList.addLast(objKpList.get(goodMatchesList.get(i).trainIdx).pt);
                sceneList.addLast(sceneKpList.get(goodMatchesList.get(i).queryIdx).pt);
            }

            // Draws some information to the frame
            String result_str = "Total Features: " + keypoints.rows() + "   Total Matches: " + matches.rows() + "   Good Matches: " + goodMatchesList.size();
            Imgproc.putText(rgb, result_str, new Point(30, rgb.rows() - 30), Core.FONT_HERSHEY_COMPLEX, 1.5, new Scalar(255, 0, 0), 2, 8, false);

            MatOfPoint2f obj = new MatOfPoint2f();
            MatOfPoint2f scene = new MatOfPoint2f();

            obj.fromList(objList);
            scene.fromList(sceneList);

            // Calculate the homography
            Mat mask = new Mat();
            Mat H = Calib3d.findHomography(obj, scene, Calib3d.RANSAC, 3, mask, 2000, 0.995);
            Mat objCorners = new Mat(4, 1, CvType.CV_32FC2);
            Mat sceneCorners = new Mat(4, 1, CvType.CV_32FC2);

            // Initializes the four corners of the object (template)
            objCorners.put(0, 0, new double[]{0, 0});
            objCorners.put(1, 0, new double[]{template.cols(), 0});
            objCorners.put(2, 0, new double[]{template.cols(), template.rows()});
            objCorners.put(3, 0, new double[]{0, template.rows()});

            // Attempts to find the corresponding four corners in the frame
            // Since it may fail and crash the app due to a bad homography result, a try-catch block is used
            try {
                Core.perspectiveTransform(objCorners, sceneCorners, H);
            } catch (CvException e) {
                e.printStackTrace();
                Log.e(TAG, "perspectiveTransform returned an assertion failed error.");
                return rgb;
            }

            // Draws the lines to the output frame
            Imgproc.line(rgb, new Point(sceneCorners.get(0, 0)), new Point(sceneCorners.get(1, 0)), new Scalar(0, 255, 0), 4);
            Imgproc.line(rgb, new Point(sceneCorners.get(1, 0)), new Point(sceneCorners.get(2, 0)), new Scalar(0, 255, 0), 4);
            Imgproc.line(rgb, new Point(sceneCorners.get(2, 0)), new Point(sceneCorners.get(3, 0)), new Scalar(0, 255, 0), 4);
            Imgproc.line(rgb, new Point(sceneCorners.get(3, 0)), new Point(sceneCorners.get(0, 0)), new Scalar(0, 255, 0), 4);

            keypoints.release();
        }
        bFrameReady = true;
        return rgb;
    }
}