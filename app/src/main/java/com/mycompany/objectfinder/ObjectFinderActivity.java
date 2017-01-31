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
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class ObjectFinderActivity extends Activity implements CvCameraViewListener2 {
    private static final String TAG = "ObjectFinderActivity";
    private static final int REQUEST_VAL = 123456;

    private CameraBridgeViewBase mOpenCvCameraView;
    private Vibrator vib;
    private TextView matchingTxt;
    private TextView templateTxt;
    private TextView frameskipTxt;

    private ArrayAdapter<String> objList;
    private ArrayList<Mat> matList;

    private FeatureDetector featuredetector;
    private DescriptorMatcher   descriptorMatcher;
    private DescriptorExtractor descriptorExtractor;

    private Rect roi;
    private Mat gray    = null;
    private Mat rgb     = null;
    private Mat clean   = null;
    private Mat template;
    private Mat templateDescriptors;
    private Mat objCorners;
    private Mat sceneCorners;
    private MatOfKeyPoint templateKeypoints;

    private boolean bIsTemplateLoaded = false;
    private boolean bFrameReady = false;
    private boolean bCalledBefore = false;
    private boolean bEnableMatching = true;
    private boolean bFrameOrRegion = false;
    private boolean bGetNewRegion = true;

    private String dialogTxt = "";
    private int frameCounter = 0;
    private int thresh = 50;
    private int countThresh = 0;

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

        setContentView(com.mycompany.objectfinder.R.layout.activity_object_finder);
        vib = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
        matchingTxt = (TextView) findViewById(R.id.object_finder_activity_match);
        templateTxt = (TextView) findViewById(R.id.object_finder_activity_template);
        frameskipTxt = (TextView) findViewById(R.id.object_finder_activity_frameskip);
        objList = new ArrayAdapter<String>(ObjectFinderActivity.this, android.R.layout.select_dialog_singlechoice);
        matList = new ArrayList<>();

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.object_finder_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);
        // Set a listener to check for a long press (>1 second when finger is on screen)
        mOpenCvCameraView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (bEnableMatching) {  //Toggles the image matching computation on/off
                    bEnableMatching = false;
                    matchingTxt.setText("Matching: OFF");
                    Toast.makeText(ObjectFinderActivity.this,
                            "Matching turned off", Toast.LENGTH_SHORT).show();
                } else {
                    bEnableMatching = true;
                    matchingTxt.setText("Matching: ON");
                    Toast.makeText(ObjectFinderActivity.this,
                            "Matching turned on", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        //Sets frame size to be this maximum, this may need to be adjusted depending on the device,
        //since each device has its own list of pre-defined supported frame sizes
        mOpenCvCameraView.setMaxFrameSize(1280, 720);
    }

    /*
    This function is automatically called upon the return to this activity after the second activity called
    via startActivityForResult() finishes, or the user presses the back button. It checks to see
    whether the ROI was successfully captured. If it is, sets the ROI and calls grabFrame().
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i(TAG, "Returned from intent");

        if(requestCode == REQUEST_VAL && resultCode == RESULT_OK) {
            Log.i(TAG, "Checking data from GetObjectActivity...");
            int topleft[] = data.getExtras().getIntArray("Point");
            int width = data.getExtras().getInt("Width");
            int height = data.getExtras().getInt("Height");
            roi = new Rect(topleft[0],topleft[1],width,height);
            bFrameOrRegion = true;
            grabFrame(null);
        }
    }

    public void saveDialog(View view)
    {
        if(bIsTemplateLoaded) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Save region");

            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            builder.setView(input);

            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialogTxt = input.getText().toString();
                    if (dialogTxt.trim().length() == 0) {
                        Toast.makeText(ObjectFinderActivity.this, "Please enter a non-empty filename", Toast.LENGTH_SHORT).show();
                        saveDialog(null);
                    } else {
                        objList.add(dialogTxt);
                        matList.add(template);
                    }
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });

            builder.show();
        }
        else
            Toast.makeText(ObjectFinderActivity.this, "ERROR: No template is loaded", Toast.LENGTH_SHORT).show();
    }

    public void frameskipDialog(View view)
    {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Skip every [1-300] frames (0 = No Skipping)");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialogTxt = input.getText().toString();
                if (dialogTxt.trim().length() == 0) {
                    Toast.makeText(ObjectFinderActivity.this, "Please enter a non-empty frameskip value", Toast.LENGTH_SHORT).show();
                    frameskipDialog(null);
                } else {
                    try {
                        countThresh = Integer.parseInt(dialogTxt);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(ObjectFinderActivity.this, "Please enter a valid frameskip value (0 - 300)", Toast.LENGTH_SHORT).show();
                        frameskipDialog(null);
                    }

                    if (countThresh > 0 && countThresh <= 300)
                        frameskipTxt.setText("Frameskip: " + countThresh);
                    else if (countThresh == 0)
                        frameskipTxt.setText("Frameskip: None");
                    else {
                        countThresh = 0;
                        frameskipTxt.setText("Frameskip: None");
                        Toast.makeText(ObjectFinderActivity.this, "Please enter a valid frameskip value (0 - 300)", Toast.LENGTH_SHORT).show();
                        frameskipDialog(null);
                    }
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    public void showList(View view){
        AlertDialog.Builder builderSingle = new AlertDialog.Builder(ObjectFinderActivity.this);
        builderSingle.setTitle("Select a region:");

        builderSingle.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builderSingle.setAdapter(objList, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                bGetNewRegion = false;
                template = matList.get(which);
                grabFrame(null);
                bGetNewRegion = true;
            }
        });
        builderSingle.show();
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
        if (gray != null)
            gray.release();
        if (rgb != null)
            rgb.release();
        if(clean != null)
            clean.release();

        gray = null;
        rgb = null;
        clean = null;
    }

    /*
    Saves the latest unmodified frame from the camera to a bitmap file, then starts another activity
    to begin the region selection
     */
    public void sendFrame(View view){
        Intent intent = new Intent(ObjectFinderActivity.this, GetObjectActivity.class);
        ///Log.i(TAG, "clean w: " + clean.cols() + " h: " + clean.rows());
        //Log.i(TAG, "clean2 w: " + clean2.cols() + " h: " + clean2.rows());
        //if(clean.rows() == 0 || clean.cols() == 0)
        // return;
        Bitmap bmp;
        /*
        if(!clean.empty()) {
            bmp = Bitmap.createBitmap(clean.cols(), clean.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(clean, bmp);
        }
        else{
            bmp = Bitmap.createBitmap(clean2.cols(), clean2.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(clean2, bmp);
        }*/
        while(clean.empty()){
            try {
                Log.i(TAG, "Waiting for Gray to hold some value...");
                Thread.sleep(50);
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
        Mat temp = clean.clone();
        /*
        Mat diag = temp.diag();
        Mat zeroes = Mat.zeros(720,1,CvType.CV_8UC4);
        //Imgproc.cvtColor(temp, temp, Imgproc.COLOR_RGBA2GRAY);
        Log.i(TAG, "clean w: " + temp.cols() + " h: " + temp.rows());
        if(diag == zeroes)
            Log.i(TAG, "diag");*/
        bmp = Bitmap.createBitmap(temp.cols(), temp.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(temp, bmp);

        String filename = createImageFromBitmap(bmp, "tempFrame");
        if(filename == null) {
            Log.e(TAG, "ERROR: Could not write bitmap to file");
            return;
        }
        startActivityForResult(intent, REQUEST_VAL);
    }

    /*
    Saves a bitmap image to a file. This allows other activities to use the image without passing the
    image via an Intent (Intents have a limit of 1MB of data that can be sent, so sending bitmaps
    is impractical).
     */
    public String createImageFromBitmap(Bitmap bitmap, String fileName) {
        //String fileName = "tempFrame";//no .png or .jpg needed
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
            FileOutputStream fo = openFileOutput("tempFrame", Context.MODE_PRIVATE);
            fo.write(bytes.toByteArray());
            // remember close file output
            fo.close();
        } catch (Exception e) {
            e.printStackTrace();
            fileName = null;
        }
        return fileName;
    }

    /*
    Returns a Mat object from a decoded bitmap saved on the device
     */
    public Mat loadMatFromImage(String fileName) {
        Mat frame = new Mat();
        try {
            // Opens bitmap file and puts it into bitmap object, then convert to Mat
            Bitmap bmp = BitmapFactory.decodeStream(this.openFileInput(fileName));
            Utils.bitmapToMat(bmp, frame);

        } catch(Exception e){
            e.printStackTrace();
            frame = null;
        }
        return frame;
    }

    /*
    Grabs the latest frame taken from the camera and uses it or a section of the frame as a template
    for future matches by extracting its features and descriptors.
     */
    public void grabFrame(View view){
        bIsTemplateLoaded = false;
        if(view != null && view.getTag().toString().equals("1"))    //Check tag to see if grabFrame is called from a button
            bFrameOrRegion = false;

        if(bFrameReady) {   //Only executes the function if there is an available frame
            //Clears the previous template features to avoid a crash that may occur
            //when indexing the kp array of a previous template with less features (out of bounds array index)
            if(bCalledBefore){  //Only executes after the first template is loaded since you cannot release a null Mat
                Log.i(TAG, "Clearing previous template's keypoints & descriptors");
                templateKeypoints.release();
                templateDescriptors.release();
            }
            if(bGetNewRegion) {
                Log.i(TAG, "Getting keypoints & descriptors of new template...");
                vib.vibrate(250);
                if (template != null)
                    template.release();
                if (bFrameOrRegion) {    //Loads ROI, crops the frame based on roi
                    Mat scene = loadMatFromImage("tempFrame");
                    if (scene == null) {
                        Log.e(TAG, "ERROR: Could not load mat from file");
                        return;
                    }
                    template = new Mat(scene, roi);
                } else    //Simply clone the grayscale frame and use it as a template
                    template = gray.clone();
            }

            templateKeypoints = new MatOfKeyPoint();
            featuredetector = FeatureDetector.create(FeatureDetector.ORB);
            descriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
            featuredetector.detect(template, templateKeypoints);

            if(templateKeypoints.rows() >= 100) {   //Extract descriptors
                Toast.makeText(ObjectFinderActivity.this,
                        "Matching tapped frame to new frames", Toast.LENGTH_LONG).show();
                templateDescriptors = new Mat();
                descriptorExtractor.compute(template, templateKeypoints, templateDescriptors);
                bIsTemplateLoaded = true;
                bCalledBefore = true;
            }
            else {  //If there is too little features (<100), it does not do matching
                Toast.makeText(ObjectFinderActivity.this,
                        "ERROR: Not enough features", Toast.LENGTH_LONG).show();
                bIsTemplateLoaded = false;
            }
        }
    }

    /*
    Sorts all of the keypoints based on its response, from largest response to smallest.
    Then, takes the top 3*N (where N = thresh). The lower this number, the faster the framerate
    but lower the accuracy and vice-versa (slight improvement, not worth reduction in accuracy).
     */
    private MatOfKeyPoint filterKP(MatOfKeyPoint keypoints){

        List<KeyPoint> listOfKeypoints = keypoints.toList();

        Collections.sort(listOfKeypoints, new Comparator<KeyPoint>() {
            @Override
            public int compare(KeyPoint kp1, KeyPoint kp2) {
                // Sort them in descending order, so the best response KPs will come first
                return (int) (kp2.response - kp1.response);
            }
        });
        if(listOfKeypoints.size()>3*thresh)
            listOfKeypoints = listOfKeypoints.subList(0,3*thresh);
        MatOfKeyPoint sortedKP = new MatOfKeyPoint();
        sortedKP.fromList(listOfKeypoints);
        return sortedKP;
    }

    /*
    Sorts all of the matches based on its distance, from smallest distance to largest.
    Then, takes the top N (where N = thresh).
     */
    private List<DMatch> filterMatches(MatOfDMatch allMatches){

        List<DMatch> allMatchesList = allMatches.toList();

        Collections.sort(allMatchesList, new Comparator<DMatch>() {
            @Override
            public int compare(DMatch m1, DMatch m2) {
                if (m1.distance < m2.distance)
                    return -1;
                if (m1.distance > m2.distance)
                    return 1;
                return 0;
            }
        });
        if(allMatchesList.size()>thresh)
            allMatchesList = allMatchesList.subList(0,thresh);
        return allMatchesList;
    }

    public void setFrameInfo(MatOfKeyPoint keypoints, List<DMatch> goodMatchesList, int numInliers){
        final MatOfKeyPoint kp = new MatOfKeyPoint(keypoints);
        final List<DMatch> matches = new ArrayList<>(goodMatchesList);
        final int inliers = numInliers;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                templateTxt.setText("Keypoints: " + kp.rows() + "  Matches: " + matches.size() + "  Inliers: " + inliers);
            }
        });
    }

    /*
    Called on every camera frame. Processes the current frame by extracting its features, matching
    the template features to it, then applying findHomography and perspectiveTransform to obtain the
    four corners of the match, which is drawn to the frame and displayed.
     */
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        if(gray != null)
            gray.release();
        if(rgb != null)
            rgb.release();
        if(clean != null)
            clean.release();

        gray = inputFrame.gray().clone();
        rgb = inputFrame.rgba().clone();
        clean = inputFrame.rgba().clone();

        Imgproc.cvtColor(rgb, rgb, Imgproc.COLOR_RGBA2RGB);
        if(bIsTemplateLoaded && bEnableMatching) { // Does not perform any computation unless a frame is tapped by the user
            if(frameCounter == 0 || countThresh == 0) {
                Log.i(TAG, "Extracting features from frame " + frameCounter);
                descriptorMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);

                // Extract keypoints and draws them to the output frame
                MatOfKeyPoint keypoints = new MatOfKeyPoint();
                featuredetector.detect(gray, keypoints);

                //Uncomment this line if you want to improve the framerate (Reduces accuracy)
                //keypoints = filterKP(keypoints);

                if (keypoints.rows() < 4)   // Minimum of 4 features needed, return if criterion is not met
                    return rgb;
                Features2d.drawKeypoints(rgb, keypoints, rgb, new Scalar(0, 255, 255), Features2d.NOT_DRAW_SINGLE_POINTS);

                // Extract descriptors from current frame
                Mat descriptors = new Mat();
                descriptorExtractor.compute(gray, keypoints, descriptors);
                MatOfDMatch matches = new MatOfDMatch();

                //Log.i(TAG, "Number of template keypoints " + templateKeypoints.rows());
                //Log.i(TAG, "Number of template descriptors " + templateDescriptors.rows());

                // Avoids crashing the code if the match() call is made before the template's descriptors are finished extracting
                if (templateDescriptors.rows() == 0 || templateKeypoints.rows() == 0)
                    return rgb;

                // Matches current frame's descriptors to template's
                descriptorMatcher.match(descriptors, templateDescriptors, matches);

                // Filters matches by taking the top [thresh] matches and putting them into a list
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

                MatOfPoint2f obj = new MatOfPoint2f();
                MatOfPoint2f scene = new MatOfPoint2f();

                obj.fromList(objList);
                scene.fromList(sceneList);

                // Calculate the homography
                Mat mask = new Mat();
                Mat H = Calib3d.findHomography(obj, scene, Calib3d.RANSAC, 5, mask, 2000, 0.995);
                //Mat H = Calib3d.findHomography(obj, scene, Calib3d.RANSAC, 5);
                objCorners = new Mat(4, 1, CvType.CV_32FC2);
                sceneCorners = new Mat(4, 1, CvType.CV_32FC2);
                int numInliers = 0;
                for (int i = 0; i < mask.rows(); i++) {
                    double value[] = mask.get(i, 0);
                    if ((int) value[0] == 1)
                        numInliers++;
                }
            /*
            double[] a = mask.get(0,0);
            Log.i(TAG, "Length: " + a.length);
            for(int i = 0; i < a.length; i++)
            Log.i(TAG, "Value: " + a[i]);*/

                // Draws some information to the frame
            /*
            String result_str1 = "Keypoints: " + keypoints.rows();
            String result_str2 = "Matches: " + goodMatchesList.size();
            String result_str3 = "Inliers: " + numInliers;
            Imgproc.putText(rgb, result_str1, new Point(20, rgb.rows() - 100), Core.FONT_HERSHEY_COMPLEX, 1, new Scalar(255, 0, 0), 2, 8, false);
            Imgproc.putText(rgb, result_str2, new Point(20, rgb.rows() - 60), Core.FONT_HERSHEY_COMPLEX, 1, new Scalar(255, 0, 0), 2, 8, false);
            Imgproc.putText(rgb, result_str3, new Point(20, rgb.rows() - 20), Core.FONT_HERSHEY_COMPLEX, 1, new Scalar(255, 0, 0), 2, 8, false);
            */
                setFrameInfo(keypoints, goodMatchesList, numInliers);

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

                keypoints.release();
            }

            if(frameCounter < countThresh) {
                frameCounter++;
            }
            else{
                frameCounter = 0;
            }

            // Draws the lines to the output frame
            Imgproc.line(rgb, new Point(sceneCorners.get(0, 0)), new Point(sceneCorners.get(1, 0)), new Scalar(0, 255, 0), 4);
            Imgproc.line(rgb, new Point(sceneCorners.get(1, 0)), new Point(sceneCorners.get(2, 0)), new Scalar(0, 255, 0), 4);
            Imgproc.line(rgb, new Point(sceneCorners.get(2, 0)), new Point(sceneCorners.get(3, 0)), new Scalar(0, 255, 0), 4);
            Imgproc.line(rgb, new Point(sceneCorners.get(3, 0)), new Point(sceneCorners.get(0, 0)), new Scalar(0, 255, 0), 4);
        }
        bFrameReady = true;
        return rgb;
    }
}