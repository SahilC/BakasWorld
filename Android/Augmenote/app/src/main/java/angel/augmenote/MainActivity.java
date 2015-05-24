package angel.augmenote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.FloatMath;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;


@SuppressWarnings("deprecation")
public class MainActivity extends Activity implements SurfaceHolder.Callback,SensorEventListener {

    Camera camera;
    SurfaceView surfaceView;
    SurfaceHolder surfaceHolder;
    boolean flag = false;
    Camera.PictureCallback rawCallback;
    Camera.ShutterCallback shutterCallback;
    Camera.PictureCallback jpegCallback;
    final String tag = "AccLogger";
    SensorManager sensore=null;

    Float mDist = new Float(0);
    String opFilePath = null;
    long totalSize = 0;

    private static final String  STATUS = "success";
    private static final String DATA = "data";
    private static final String HEIGHT = "height";
    private static  final String LEFT = "left";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        sensore = (SensorManager) getSystemService(SENSOR_SERVICE);
        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        surfaceHolder.addCallback(this);

        // deprecated setting, but required on Android versions prior to 3.0
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        jpegCallback = new Camera.PictureCallback() {
            public void onPictureTaken(byte[] data, Camera camera) {
                FileOutputStream outStream = null;
                try {
                    String filePath = Environment
                            .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) +
                            File.separator + Config.IMAGE_DIRECTORY_NAME;
                    File dirFile = mkDir(filePath);
                    File outputFile = new File(dirFile, String.format("%d.png", System.currentTimeMillis()));
                    outStream = new FileOutputStream(outputFile);

                    outStream.write(data);
                    outStream.close();
                    opFilePath = outputFile.getAbsolutePath();
                    UploadFileToServer uploadFileToServer = new UploadFileToServer();
                    uploadFileToServer.execute();
                    Log.d("Log", "onPictureTaken - wrote bytes: " + data.length);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                }
                Toast.makeText(getApplicationContext(), "Augmentizing", 2000).show();
                refreshCamera();
            }

            private File mkDir(String dirPath) {
                File dirFile = new File(dirPath);
                if (!dirFile.exists()) {
                    if (!dirFile.mkdirs()) {
                        Log.d("Log", "Oops! Failed create "
                                + Config.IMAGE_DIRECTORY_NAME + " directory");

                    }
                }
                return dirFile;
            }


        };
    }

    /**
     * Method to show alert dialog
     */
    private void showAlert(String message) {
//        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//        View v = inflater.inflate(R.layout.activity_main,null);

        TextView t1 = (TextView) findViewById(R.id.textView4);
        try {
            JSONObject json = new JSONObject(message);
            if(json.getBoolean(STATUS)) {

                JSONArray jsonArray = json.getJSONArray(DATA);
                StringBuffer buffer = new StringBuffer();
                for(int count = 0 ; count < jsonArray.length(); count++){
                    buffer.append("- ").append(jsonArray.getString(count)).append("\n\n");
                }

                t1.setText(buffer.toString());

                t1.bringToFront();

                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) t1.getLayoutParams();

                Display display = getWindowManager().getDefaultDisplay();
                Point size = new Point();
                display.getSize(size);


                p.leftMargin = size.x/4;
                p.topMargin = size.y / 2 ;
                t1.setLayoutParams(p);


//        setContentView(v);
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setMessage(message).setTitle("Response from Servers")
//                .setCancelable(false)
//                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int id) {
//                        // do nothing
//                    }
//                });
//        AlertDialog alert = builder.create();
//        alert.show();
            }
            else{
                t1.setText("No Notes Detected!!");

                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) t1.getLayoutParams();

                Display display = getWindowManager().getDefaultDisplay();
                Point size = new Point();
                display.getSize(size);

                t1.bringToFront();
                p.leftMargin = size.x/4;
                p.topMargin = size.y/2;
                t1.setLayoutParams(p);
            }
        }catch(JSONException e){
            e.printStackTrace();
        }
    }

    public void captureImage(View v) throws IOException {
        //take the picture
        camera.takePicture(null, null, jpegCallback);
    }

    public void refreshCamera() {
        if (surfaceHolder.getSurface() == null) {
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            camera.stopPreview();
        } catch (Exception e) {
            // ignore: tried to stop a non-existent preview
        }
        Display display = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
        if(display.getRotation() == Surface.ROTATION_0)
        {
//            parameters.setPreviewSize(height, width);
            camera.setDisplayOrientation(90);
        }

        if(display.getRotation() == Surface.ROTATION_90)
        {
//            parameters.setPreviewSize(width, height);
        }

        if(display.getRotation() == Surface.ROTATION_180)
        {
//            parameters.setPreviewSize(height, width);
        }

        if(display.getRotation() == Surface.ROTATION_270)
        {
//            parameters.setPreviewSize(width, height);
            camera.setDisplayOrientation(180);
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here
        // start preview with new settings
        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
        } catch (Exception e) {

        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // Now that the size is known, set up the camera parameters and begin
        // the preview.
        Camera.Parameters param = camera.getParameters();
        camera.setParameters(param);
        refreshCamera();
    }

    public void surfaceCreated(SurfaceHolder holder) {
        try {
            // open the camera
            camera = Camera.open();
        } catch (RuntimeException e) {
            // check for exceptions
            System.err.println(e);
            return;
        }
        Camera.Parameters param;
        param = camera.getParameters();

        // modify parameter
//        param.setPreviewSize(288, 320);
        param.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        param.setSceneMode(Camera.Parameters.SCENE_MODE_PORTRAIT);

        camera.setParameters(param);
        try {
            // The Surface has been created, now tell the camera where to draw
            // the preview.
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
            camera.startFaceDetection();
        } catch (Exception e) {
            // check for exceptions
            System.err.println(e);
            return;
        }


    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // stop preview and release camera
        camera.stopFaceDetection();
        camera.stopPreview();
        camera.release();
        camera = null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Get the pointer ID
        Camera.Parameters params = camera.getParameters();
        int action = event.getAction();


        if (event.getPointerCount() > 1) {
            // handle multi-touch events
            if (action == MotionEvent.ACTION_POINTER_DOWN) {
                mDist = getFingerSpacing(event);
            } else if (action == MotionEvent.ACTION_MOVE && params.isZoomSupported()) {
                camera.cancelAutoFocus();
                handleZoom(event, params);
            }
        } else {
            // handle single touch events
            if (action == MotionEvent.ACTION_UP) {
                handleFocus(event, params);
            }
        }
        return true;
    }

    private void handleZoom(MotionEvent event, Camera.Parameters params) {
        int maxZoom = params.getMaxZoom();
        int zoom = params.getZoom();
        float newDist = getFingerSpacing(event);
        if (newDist > mDist) {
            //zoom in
            if (zoom < maxZoom)
                zoom++;
        } else if (newDist < mDist) {
            //zoom out
            if (zoom > 0)
                zoom--;
        }
        mDist = newDist;
        params.setZoom(zoom);
        camera.setParameters(params);
    }

    public void handleFocus(MotionEvent event, Camera.Parameters params) {
        int pointerId = event.getPointerId(0);
        int pointerIndex = event.findPointerIndex(pointerId);
        // Get the pointer's current position
        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);

        List<String> supportedFocusModes = params.getSupportedFocusModes();
        if (supportedFocusModes != null && supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            camera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean b, Camera camera) {
                    // currently set to auto-focus on single touch
                }
            });
        }
    }

    /**
     * Determine the space between the first two fingers
     */
    private float getFingerSpacing(MotionEvent event) {
        // ...
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return FloatMath.sqrt(x * x + y * y);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Uploading the file to server
     */
    private class UploadFileToServer extends AsyncTask<Void, Integer, String> {
        @Override
        protected void onPreExecute() {
            // setting progress bar to zero
//            progressBar.setProgress(0);
            super.onPreExecute();
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            // Making progress bar visible
//            progressBar.setVisibility(View.VISIBLE);

            // updating progress bar value
//            progressBar.setProgress(progress[0]);

            // updating percentage value
//            txtPercentage.setText(String.valueOf(progress[0]) + "%");
        }

        @Override
        protected String doInBackground(Void... params) {
            return uploadFile();
        }

        private String uploadFile() {
            String responseString = null;
            Log.d("Log", "File path" + opFilePath);
            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = new HttpPost(Config.FILE_UPLOAD_URL);
            try {
                AndroidMultiPartEntity entity = new AndroidMultiPartEntity(
                        new AndroidMultiPartEntity.ProgressListener() {

                            @Override
                            public void transferred(long num) {
                                publishProgress((int) ((num / (float) totalSize) * 100));
                            }
                        });
                ExifInterface newIntef = new ExifInterface(opFilePath);
                newIntef.setAttribute(ExifInterface.TAG_ORIENTATION,String.valueOf(2));
                File file = new File(opFilePath);
                entity.addPart("pic", new FileBody(file));
                totalSize = entity.getContentLength();
                httppost.setEntity(entity);

                // Making server call
                HttpResponse response = httpclient.execute(httppost);
                HttpEntity r_entity = response.getEntity();


                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    // Server response
                    responseString = EntityUtils.toString(r_entity);
                    Log.d("Log", responseString);
                } else {
                    responseString = "Error occurred! Http Status Code: "
                            + statusCode + " -> " + response.getStatusLine().getReasonPhrase();
                    Log.d("Log", responseString);
                }

            } catch (ClientProtocolException e) {
                responseString = e.toString();
            } catch (IOException e) {
                responseString = e.toString();
            }

            return responseString;

        }

        @Override
        protected void onPostExecute(String result) {
            Log.e("Log", "Response from server: " + result);


            // showing the server response in an alert dialog
            showAlert(result);

            super.onPostExecute(result);
        }
    }

    public void onSensorChanged(SensorEvent event){
        Sensor sensor = event.sensor;
        float [] values = event.values;
        synchronized (this) {
            float x = values[0];
            float y = values[1];
            float z = values[2];

            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER && (x > 13 || y > 13 || z > 13)) {
                TextView t1 = (TextView) findViewById(R.id.textView4);
                t1.setText("");
            }
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(tag,"onAccuracyChanged: " + sensor + ", accuracy: " + accuracy);
    }

    protected void onResume() {
        super.onResume();
        Sensor Accel = sensore.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        // register this class as a listener for the orientation and accelerometer sensors
        sensore.registerListener((SensorEventListener) this, Accel,        SensorManager.SENSOR_DELAY_FASTEST);
    }

}
