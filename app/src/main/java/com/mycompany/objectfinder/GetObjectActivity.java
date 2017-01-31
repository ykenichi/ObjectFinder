package com.mycompany.objectfinder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.core.Point;

import java.io.IOException;
import java.util.Arrays;

public class GetObjectActivity extends Activity {
    private static final String TAG = "GetObjectActivity";

    private ImageView CameraFrame;
    private TextView selectedCoordinates;
    private TextView helpTxt;
    private Toast toast;
    private float scalex;
    private float scaley;
    private int width;
    private int height;
    private Bitmap originalBitmap;
    private Bitmap modifiedBitmap;
    private float p1[] = {0, 0};
    private float p2[] = {0, 0};
    private int state = 0;
    private boolean isValid = false;
    private String dialogTxt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_object);
        CameraFrame = (ImageView) findViewById(R.id.get_object_activity_imageview);
        selectedCoordinates = (TextView) findViewById(R.id.get_object_activity_coords);
        helpTxt = (TextView) findViewById(R.id.get_object_activity_help);
        toast = Toast.makeText(this, "", Toast.LENGTH_SHORT);

        try {   //Decodes the bitmap, then copies it to another bitmap object since the original is immutable
            originalBitmap = BitmapFactory.decodeStream(this.openFileInput("tempFrame"));
            modifiedBitmap = originalBitmap.copy(originalBitmap.getConfig(), true);
            CameraFrame.setImageBitmap(modifiedBitmap);
        } catch(Exception e){
            e.printStackTrace();
        }
        ViewTreeObserver vto = CameraFrame.getViewTreeObserver();
        vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() { //Used to get ImageView dimensions
            public boolean onPreDraw() {
                CameraFrame.getViewTreeObserver().removeOnPreDrawListener(this);
                float ivHeight = CameraFrame.getMeasuredHeight();
                float ivWidth = CameraFrame.getMeasuredWidth();
                scalex = modifiedBitmap.getWidth() / ivWidth;
                scaley = modifiedBitmap.getHeight() / ivHeight;
                return true;
            }
        });
        CameraFrame.setOnTouchListener(new View.OnTouchListener() { //Listens for taps and their X,Y coords
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    float temp[] = {event.getX() * scalex, event.getY() * scaley};
                    Paint paint = new Paint();
                    paint.setAntiAlias(true);
                    paint.setColor(Color.RED);
                    paint.setStrokeWidth(3);
                    paint.setStyle(Paint.Style.STROKE);
                    Canvas canvas = new Canvas(modifiedBitmap);
                    Log.i(TAG, "Tapped Coords: x=" + temp[0] + " y=" + temp[1]);

                    if (state == 0) {   //First time screen is tapped, draw/save point 1
                        helpTxt.setText(R.string.helpTxt2);
                        Log.i(TAG, "Drawing point 1");
                        canvas.drawCircle(temp[0], temp[1], 20, paint);
                        state++;
                        p1[0] = temp[0];
                        p1[1] = temp[1];
                    } else if (state == 1) {   //Second time screen is tapped, draw/save point 2
                        helpTxt.setText(R.string.helpTxt3);
                        Log.i(TAG, "Drawing point 2");
                        canvas.drawCircle(temp[0], temp[1], 20, paint);
                        state++;
                        p2[0] = temp[0];
                        p2[1] = temp[1];

                        paint.setColor(Color.CYAN);
                        width = (int) Math.abs(p2[0] - p1[0]);
                        height = (int) Math.abs(p2[1] - p1[1]);
                        //float p3[] = {p2[0], p1[1]};
                        //float p4[] = {p1[0], p2[1]};

                        if (width >= 100 && height >= 100) {   //Draw box around region
                            toast.setText("Object successfully marked");
                            toast.show();
                            canvas.drawLine(p1[0], p1[1], p2[0], p1[1], paint);
                            canvas.drawLine(p2[0], p1[1], p2[0], p2[1], paint);
                            canvas.drawLine(p2[0], p2[1], p1[0], p2[1], paint);
                            canvas.drawLine(p1[0], p2[1], p1[0], p1[1], paint);
                            isValid = true;
                        } else {    //Region is too small to gain a meaningful number of features
                            toast.setText("Object not marked. Minimum size for region is 100x100");
                            toast.show();
                        }
                    }
                    selectedCoordinates.setText("P1 = [" + (int) p1[0] + ", " + (int) p1[1] + "]\nP2 = [" + (int) p2[0] + ", " + (int) p2[1] + "]");
                    CameraFrame.setImageBitmap(modifiedBitmap);
                }
                return false;
            }
        });
    }

    /*
    Saves the top-left coordinate, width, and height to an intent to send it over to the main activity.
    Calls finish(), which ends this activity and returns back to the activity that called it.
     */
    public void returnResult(View view){
        Intent intent = new Intent();
        if(isValid) {

            int topLeft[] = {(int)Math.min(p1[0], p2[0]), (int)Math.min(p1[1], p2[1])};
            intent.putExtra("Point", topLeft);
            intent.putExtra("Width", width);
            intent.putExtra("Height", height);
            setResult(RESULT_OK, intent);
        }
        else
            setResult(RESULT_CANCELED);
        finish();
    }
    /*
    Sets variables back to their default values.
     */
    public void clearPoints(View view){
        p1[0] = 0;
        p1[1] = 0;
        p2[0] = 0;
        p2[1] = 0;
        width = 0;
        height = 0;
        state = 0;
        isValid = false;
        modifiedBitmap = originalBitmap.copy(originalBitmap.getConfig(), true);
        CameraFrame.setImageBitmap(modifiedBitmap);
        selectedCoordinates.setText(R.string.coordTxt);
        helpTxt.setText(R.string.helpTxt1);
    }
}
