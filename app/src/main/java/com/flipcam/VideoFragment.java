package com.flipcam;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.flipcam.cameramanager.Camera1Manager;
import com.flipcam.util.GLUtil;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static android.os.Environment.getExternalStoragePublicDirectory;
import static com.flipcam.PermissionActivity.FC_SHARED_PREFERENCE;


public class VideoFragment extends Fragment implements SurfaceHolder.Callback, SurfaceTexture.OnFrameAvailableListener{
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER

    public static final String TAG = "VideoFragment";
    private static int VIDEO_WIDTH = 640;  // dimensions for VGA
    private static int VIDEO_HEIGHT = 480;
    CamcorderProfile camcorderProfile;
    private SurfaceView cameraView;
    final int FRAME_AVAILABLE = 1000;
    final int RECORD_STOP = 2000;
    final int RECORD_START = 3000;
    final int SHUTDOWN = 6000;
    final int RECORD_COMPLETE = 7000;
    SurfaceTexture surfaceTexture;
    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLConfig mEGLConfig = null;
    // Android-specific extension.
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    public static final int FLAG_RECORDABLE = 0x01;
    private int mProgramHandle;
    private int mTextureTarget;
    private int mTextureId;
    private int muMVPMatrixLoc;
    private static final int SIZEOF_FLOAT = 4;
    private int muTexMatrixLoc;
    private int maPositionLoc;
    private int maTextureCoordLoc;
    /**
     * A "full" square, extending from -1 to +1 in both dimensions.  When the model/view/projection
     * matrix is identity, this will exactly cover the viewport.
     * <p>
     * The texture coordinates are Y-inverted relative to RECTANGLE.  (This seems to work out
     * right with external textures from SurfaceTexture.)
     */
    private static final float FULL_RECTANGLE_COORDS[] = {
            -1.0f, -1.0f,   // 0 bottom left
            1.0f, -1.0f,   // 1 bottom right
            -1.0f,  1.0f,   // 2 top left
            1.0f,  1.0f,   // 3 top right
    };
    private static final float FULL_RECTANGLE_TEX_COORDS[] = {
            0.0f, 0.0f,     // 0 bottom left
            1.0f, 0.0f,     // 1 bottom right
            0.0f, 1.0f,     // 2 top left
            1.0f, 1.0f      // 3 top right
    };
    //Surface onto which camera frames are drawn
    EGLSurface eglSurface;
    //Surface to which camera frames are sent for encoding to mp4 format
    EGLSurface encoderSurface=null;
    SurfaceHolder camSurfHolder=null;
    private final float[] mTmpMatrix = new float[16];
    public static final float[] IDENTITY_MATRIX;
    public static final float[] RECORD_IDENTITY_MATRIX;
    static {
        IDENTITY_MATRIX = new float[16];
        Matrix.setIdentityM(IDENTITY_MATRIX, 0);
        RECORD_IDENTITY_MATRIX = new float[16];
        Matrix.setIdentityM(RECORD_IDENTITY_MATRIX, 0);}

    // Simple vertex shader, used for all programs.
    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
                    "uniform mat4 uTexMatrix;\n" +
                    "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTextureCoord;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = uMVPMatrix * aPosition;\n" +
                    "    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
                    "}\n";

    // Simple fragment shader for use with external 2D textures (e.g. what we get from
    // SurfaceTexture).
    private static final String FRAGMENT_SHADER_EXT =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                    "}\n";

    CameraRenderer.CameraHandler cameraHandler;
    MainHandler mainHandler;
    Object renderObj = new Object();
    volatile boolean isReady=false;
    boolean VERBOSE=false;
    //Keep in portrait by default.
    boolean portrait=true;
    int orientation = -1;
    float rotationAngle = 0.0f;
    boolean backCamera = true;
    volatile boolean isRecord = false;
    MediaRecorder mediaRecorder = null;
    int frameCount=0;
    volatile int cameraFrameCnt=0;
    volatile int frameCnt=0;
    Camera1Manager camera1;
    FrameLayout frameLayout;
    public VideoFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment VideoFragment.
     */
    public static VideoFragment newInstance() {
        VideoFragment fragment = new VideoFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_video, container, false);
        Log.d(TAG,"Inside video fragment");
        ImageView substitute = (ImageView)view.findViewById(R.id.substitute);
        substitute.setVisibility(View.INVISIBLE);
        //ImageView thumbnail = (ImageView)view.findViewById(R.id.thumbnail);
        //fetchMedia(thumbnail);
        cameraView = (SurfaceView) view.findViewById(R.id.cameraSurfaceView);
        cameraView.getHolder().addCallback(this);
        camera1 = Camera1Manager.getInstance();
        frameLayout =(FrameLayout)view.findViewById(R.id.cameraView);
        return view;
    }

    private void fetchMedia(ImageView thumbnail)
    {
        String removableStoragePath;
        Log.d(TAG,"storage state = "+Environment.getExternalStorageState());
        File fileList[] = new File("/storage/").listFiles();
        //To find location of SD Card, if it exists
        for (File file : fileList)
        {
            if(!file.getAbsolutePath().equalsIgnoreCase(Environment.getExternalStorageDirectory().getAbsolutePath()) && file.isDirectory() && file.canRead()) {
                removableStoragePath = file.getAbsolutePath();
                Log.d(TAG,removableStoragePath);
                File newDir = new File(removableStoragePath+"/FC_Media");
                if(!newDir.exists()){
                    newDir.mkdir();
                }
                /*for(File file1 : new File(removableStoragePath).listFiles())
                {
                    Log.d(TAG,"SD Card path = "+file1.getPath());
                }*/
            }
        }
        //Storing in public folder. This will ensure that the files are visible in other apps as well.
        File root = getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        Log.d(TAG,root.getPath());
        File fc = new File(root.getPath()+"/FlipCam");
        if(!fc.exists()){
            fc.mkdir();
            Log.d(TAG,"FlipCam dir created");
            File videos = new File(fc.getPath()+"/FC_Videos");
            if(!videos.exists()){
                videos.mkdir();
                Log.d(TAG,"Videos dir created");
            }
            File images = new File(fc.getPath()+"/FC_Images");
            if(!images.exists()){
                images.mkdir();
                Log.d(TAG,"Images dir created");
            }
        }
        //Use this for sharing files between apps
        File videos = new File(root.getPath()+"/FlipCam/FC_Videos/");
        if(videos.listFiles() != null){
            Log.d(TAG,"List = "+videos.listFiles());
            Log.d(TAG,"List length = "+videos.listFiles().length);
            for(File file : videos.listFiles()){
                Log.d(TAG,file.getPath());
                /*final Bitmap img = BitmapFactory.decodeFile(file.getPath());
                thumbnail.setImageBitmap(img.createScaledBitmap(img,68,68,false));*/
                final String imgPath = file.getPath();
                Bitmap vid = ThumbnailUtils.createVideoThumbnail(file.getPath(), MediaStore.Video.Thumbnails.MICRO_KIND);
                thumbnail.setImageBitmap(vid.createScaledBitmap(vid,68,68,false));
                thumbnail.setOnClickListener(new View.OnClickListener(){
                    @Override
                    public void onClick(View view)
                    {
                        openMedia(imgPath);
                    }
                });
                break;
            }
        }
    }

    private void openMedia(String path)
    {
        setCameraClose();
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse("file://"+path),"video/*");
        startActivity(intent);
    }

    private void setCameraClose()
    {
        //Set this if you want to continue when the launcher activity resumes.
        SharedPreferences.Editor editor = getActivity().getSharedPreferences(FC_SHARED_PREFERENCE,Context.MODE_PRIVATE).edit();
        editor.putBoolean("startCamera",false);
        editor.commit();
    }

    private void setCameraQuit()
    {
        //Set this if you want to quit the app when launcher activity resumes.
        SharedPreferences.Editor editor = getActivity().getSharedPreferences(FC_SHARED_PREFERENCE,Context.MODE_PRIVATE).edit();
        editor.putBoolean("startCamera",true);
        editor.commit();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    class MainHandler extends Handler {
        WeakReference<VideoFragment> recordVid;

        public MainHandler(VideoFragment recordVideo) {
            recordVid = new WeakReference<>(recordVideo);
        }

        @Override
        public void handleMessage(Message msg) {

            switch(msg.what)
            {
                case FRAME_AVAILABLE:
                    Log.d(TAG, "Calling drawFrame()....");
                    //audioVideoRecording.drawFrame();
                    Log.d(TAG, "drawFrame() done....");
                    break;
                case RECORD_COMPLETE:
                    //displayComplete();
                    break;
            }
        }
    }

    void waitUntilReady()
    {
        Log.d(TAG,"Waiting....");
        synchronized (renderObj)
        {
            while(!isReady){
                try {
                    renderObj.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        Log.d(TAG,"Come out of WAIT");
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        if(VERBOSE)Log.d(TAG,"FRAME Available now");
        if(VERBOSE)Log.d(TAG,"is Record = "+isRecord);
        if(isRecord){
            if(VERBOSE)Log.d(TAG,"Frame avail cnt = "+(++cameraFrameCnt));
        }
        cameraHandler.sendEmptyMessage(FRAME_AVAILABLE);
    }

    private void prepareEGLDisplayandContext()
    {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            mEGLDisplay = null;
            throw new RuntimeException("unable to initialize EGL14");
        }
        EGLConfig config = getConfig(FLAG_RECORDABLE, 2);
        if (config == null) {
            throw new RuntimeException("Unable to find a suitable EGLConfig");
        }
        int[] attrib2_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        EGLContext context = EGL14.eglCreateContext(mEGLDisplay, config, EGL14.EGL_NO_CONTEXT,
                attrib2_list, 0);
        checkEglError("eglCreateContext");
        mEGLConfig = config;
        mEGLContext = context;

        // Confirm with query.
        int[] values = new int[1];
        EGL14.eglQueryContext(mEGLDisplay, mEGLContext, EGL14.EGL_CONTEXT_CLIENT_VERSION,
                values, 0);
        Log.d(TAG, "EGLContext created, client version " + values[0]);
    }

    private void checkEglError(String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }

    private EGLConfig getConfig(int flags, int version) {
        int renderableType = EGL14.EGL_OPENGL_ES2_BIT;

        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, renderableType,
                EGL14.EGL_NONE, 0,      // placeholder for recordable [@-3]
                EGL14.EGL_NONE
        };
        if ((flags & FLAG_RECORDABLE) != 0) {
            attribList[attribList.length - 3] = EGL_RECORDABLE_ANDROID;
            attribList[attribList.length - 2] = 1;
        }
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length,
                numConfigs, 0)) {
            Log.w(TAG, "unable to find RGB8888 / " + version + " EGLConfig");
            return null;
        }
        return configs[0];
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG,"Setting up camera = "+camSurfHolder+", surfaceTexture = "+surfaceTexture);
        if(camSurfHolder != null && !camera1.isCameraReady() && surfaceTexture!=null) {
            camera1.openCamera(backCamera);
            camera1.setResolution(getActivity().getWindowManager());
            camera1.setFPS();
            setLayoutAspectRatio();
            camera1.startPreview(surfaceTexture);
        }
    }

    public void setLayoutAspectRatio()
    {
        // Set the preview aspect ratio.
        ViewGroup.LayoutParams layoutParams = cameraView.getLayoutParams();
        VIDEO_WIDTH = camera1.getPreviewSizes()[0];
        VIDEO_HEIGHT = camera1.getPreviewSizes()[1];
        int temp = VIDEO_HEIGHT;
        VIDEO_HEIGHT = VIDEO_WIDTH;
        VIDEO_WIDTH = temp;
        layoutParams.height = VIDEO_HEIGHT;
        layoutParams.width = VIDEO_WIDTH;
        Log.d(TAG,"LP Height = "+layoutParams.height);
        Log.d(TAG,"LP Width = "+layoutParams.width);
        if(!portrait) {
            temp = VIDEO_HEIGHT;
            VIDEO_HEIGHT = VIDEO_WIDTH;
            VIDEO_WIDTH = temp;
        }
        int degree;
        if(backCamera) {
            degree = 180;
        }
        else{
            degree = 0;
        }
        Log.d(TAG,"Orientation == "+camera1.getCameraInfo().orientation);
        int result = (camera1.getCameraInfo().orientation + degree) % 360;
        result = (360 - result) % 360;
        Log.d(TAG,"Result == "+result);
        camera1.setDisplayOrientation(result);
    }

    private void releaseEGLSurface(){
        EGL14.eglDestroySurface(mEGLDisplay,eglSurface);
    }

    private void releaseProgram(){
        GLES20.glDeleteProgram(mProgramHandle);
    }

    private void releaseEGLContext()
    {
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(mEGLDisplay);
        }
        mEGLDisplay = EGL14.EGL_NO_DISPLAY;
        mEGLContext = EGL14.EGL_NO_CONTEXT;
        mEGLConfig = null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG,"Fragment destroy...app is being minimized");
        setCameraClose();
        super.onDestroy();
    }

    @Override
    public void onStop() {
        Log.d(TAG,"Fragment stop...app is out of focus");
        super.onStop();
    }

    @Override
    public void onPause() {
        Log.d(TAG,"Fragment pause....app is being quit");
        Log.d(TAG,"cameraHandler = "+cameraHandler);
        if(cameraHandler!=null) {
            cameraHandler.sendEmptyMessage(SHUTDOWN);
        }
        if(surfaceTexture!=null){
            surfaceTexture.release();
            surfaceTexture=null;
        }
        camera1.stopPreview();
        camera1.releaseCamera();
        setCameraQuit();
        //orientationEventListener.disable();
        releaseEGLSurface();
        releaseProgram();
        releaseEGLContext();
        super.onPause();
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfCreated holder = " + surfaceHolder);
        camSurfHolder = surfaceHolder;
        mainHandler = new MainHandler(this);
        prepareEGLDisplayandContext();
        CameraRenderer cameraRenderer = new CameraRenderer();
        cameraRenderer.start();
        waitUntilReady();
        //showPreview();
        if(!camera1.isCameraReady()) {
            camera1.openCamera(backCamera);
            camera1.setResolution(getActivity().getWindowManager());
            camera1.setFPS();
            setLayoutAspectRatio();
            camera1.startPreview(surfaceTexture);
        }
        surfaceTexture.setOnFrameAvailableListener(this);
        //When recreate() is called, this is called again and recording needs to begin.
        if(isRecord){
            Log.d(TAG,"send record start");
            cameraHandler.sendEmptyMessage(RECORD_START);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged fmt=" + format + " size=" + width + "x" + height +
                " holder=" + surfaceHolder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceDestroyed holder=" + surfaceHolder);
    }

    class CameraRenderer extends Thread
    {
        int recordStop = -1;
        boolean isRecording = false;

        public CameraRenderer()
        {

        }

        @Override
        public void run()
        {
            Looper.prepare();
            cameraHandler = new CameraHandler(this);
            createSurfaceTexture();
            synchronized (renderObj){
                isReady=true;
                renderObj.notify();
            }
            if(VERBOSE)Log.d(TAG,"Main thread notified");
            Looper.loop();
            Log.d(TAG,"Camera Renderer STOPPED");
        }

        private void makeCurrent(EGLSurface surface)
        {
            EGL14.eglMakeCurrent(mEGLDisplay, surface, surface, mEGLContext);
        }
        /**
         * Creates a texture object suitable for use with this program.
         * <p>
         * On exit, the texture will be bound.
         */
        public int createGLTextureObject() {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            GLUtil.checkGlError("glGenTextures");

            int texId = textures[0];
            GLES20.glBindTexture(mTextureTarget, texId);
            GLUtil.checkGlError("glBindTexture " + texId);

            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_NEAREST);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLUtil.checkGlError("glTexParameter");

            return texId;
        }

        private EGLSurface prepareWindowSurface(Surface surface)
        {
            // Create a window surface, and attach it to the Surface we received.
            int[] surfaceAttribs = {
                    EGL14.EGL_NONE
            };
            EGLSurface surface1;
            surface1 = EGL14.eglCreateWindowSurface(mEGLDisplay, mEGLConfig,surface ,
                    surfaceAttribs, 0);
            checkEglError("eglCreateWindowSurface");
            if (surface1 == null) {
                throw new RuntimeException("surface was null");
            }
            return surface1;
        }

        void createSurfaceTexture()
        {
            eglSurface = prepareWindowSurface(camSurfHolder.getSurface());
            makeCurrent(eglSurface);

            mProgramHandle = GLUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT);

            maPositionLoc = GLES20.glGetAttribLocation(mProgramHandle, "aPosition");
            GLUtil.checkLocation(maPositionLoc, "aPosition");
            maTextureCoordLoc = GLES20.glGetAttribLocation(mProgramHandle, "aTextureCoord");
            GLUtil.checkLocation(maTextureCoordLoc, "aTextureCoord");
            muMVPMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uMVPMatrix");
            GLUtil.checkLocation(muMVPMatrixLoc, "uMVPMatrix");
            muTexMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uTexMatrix");
            GLUtil.checkLocation(muTexMatrixLoc, "uTexMatrix");
            mTextureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
            mTextureId = createGLTextureObject();
            surfaceTexture = new SurfaceTexture(mTextureId);
        }
        /**
         * Issues the draw call.  Does the full setup on every call.
         *
         * @param mvpMatrix The 4x4 projection matrix.
         * @param vertexBuffer Buffer with vertex position data.
         * @param firstVertex Index of first vertex to use in vertexBuffer.
         * @param vertexCount Number of vertices in vertexBuffer.
         * @param coordsPerVertex The number of coordinates per vertex (e.g. x,y is 2).
         * @param vertexStride Width, in bytes, of the position data for each vertex (often
         *        vertexCount * sizeof(float)).
         * @param texMatrix A 4x4 transformation matrix for texture coords.  (Primarily intended
         *        for use with SurfaceTexture.)
         * @param texBuffer Buffer with vertex texture data.
         * @param texStride Width, in bytes, of the texture data for each vertex.
         */
        private void draw(float[] mvpMatrix, FloatBuffer vertexBuffer, int firstVertex,
                          int vertexCount, int coordsPerVertex, int vertexStride,
                          float[] texMatrix, FloatBuffer texBuffer, int textureId, int texStride) {
            GLUtil.checkGlError("draw start");

            // Select the program.
            GLES20.glUseProgram(mProgramHandle);
            GLUtil.checkGlError("glUseProgram");

            // Set the texture.
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(mTextureTarget, textureId);

            // Copy the model / view / projection matrix over.
            GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mvpMatrix, 0);
            GLUtil.checkGlError("glUniformMatrix4fv");

            // Copy the texture transformation matrix over.
            GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, texMatrix, 0);
            GLUtil.checkGlError("glUniformMatrix4fv");

            // Enable the "aPosition" vertex attribute.
            GLES20.glEnableVertexAttribArray(maPositionLoc);
            GLUtil.checkGlError("glEnableVertexAttribArray");

            // Connect vertexBuffer to "aPosition".
            GLES20.glVertexAttribPointer(maPositionLoc, coordsPerVertex,
                    GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);
            GLUtil.checkGlError("glVertexAttribPointer");

            // Enable the "aTextureCoord" vertex attribute.
            GLES20.glEnableVertexAttribArray(maTextureCoordLoc);
            GLUtil.checkGlError("glEnableVertexAttribArray");

            // Connect texBuffer to "aTextureCoord".
            GLES20.glVertexAttribPointer(maTextureCoordLoc, 2,
                    GLES20.GL_FLOAT, false, texStride, texBuffer);
            GLUtil.checkGlError("glVertexAttribPointer");

            // Draw the rect.
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, firstVertex, vertexCount);
            GLUtil.checkGlError("glDrawArrays");

            // Done -- disable vertex array, texture, and program.
            GLES20.glDisableVertexAttribArray(maPositionLoc);
            GLES20.glDisableVertexAttribArray(maTextureCoordLoc);
            GLES20.glBindTexture(mTextureTarget, 0);
            GLES20.glUseProgram(0);
        }

        /**
         * Allocates a direct float buffer, and populates it with the float array data.
         */
        private FloatBuffer createFloatBuffer(float[] coords) {
            // Allocate a direct ByteBuffer, using 4 bytes per float, and copy coords into it.
            ByteBuffer bb = ByteBuffer.allocateDirect(coords.length * SIZEOF_FLOAT);
            bb.order(ByteOrder.nativeOrder());
            FloatBuffer fb = bb.asFloatBuffer();
            fb.put(coords);
            fb.position(0);
            return fb;
        }

        String mNextVideoAbsolutePath = null;
        void setupMediaRecorder()
        {
            camcorderProfile = CamcorderProfile.get(camera1.getCameraId(),CamcorderProfile.QUALITY_HIGH);
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
                mNextVideoAbsolutePath = getVideoFilePath();
            }
            mediaRecorder.setOutputFile(mNextVideoAbsolutePath);
            mediaRecorder.setVideoEncodingBitRate(camcorderProfile.videoBitRate);
            mediaRecorder.setVideoFrameRate(camcorderProfile.videoFrameRate);
            mediaRecorder.setVideoSize(VIDEO_WIDTH, VIDEO_HEIGHT);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            try {
                mediaRecorder.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                encoderSurface = prepareWindowSurface(mediaRecorder.getSurface());
            }
        }

        private String getVideoFilePath() {
            File dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM+"/FlipCam/FC_Videos/");
            if(!dcim.exists())
            {
                dcim.mkdirs();
            }
            String path = dcim.getPath()+"FC_VID_"+System.currentTimeMillis()+".mp4";
            Log.d(TAG,"Saving media file at = "+path);
            return path;
        }

        void drawFrame()
        {
            if(mEGLConfig!=null && camera1.isCameraReady()) {
                makeCurrent(eglSurface);
                if(VERBOSE) Log.d(TAG,"made current");
                //Get next frame from camera
                surfaceTexture.updateTexImage();
                surfaceTexture.getTransformMatrix(mTmpMatrix);

                //Fill the surfaceview with Camera frame
                int viewWidth = cameraView.getWidth();
                int viewHeight = cameraView.getHeight();
                if (frameCount == 0) {
                    if(VERBOSE)Log.d(TAG, "FRAME Count = "+frameCount);
                    Log.d(TAG,"SV Width == "+viewWidth+", SV Height == "+viewHeight);
                }
                GLES20.glViewport(0, 0, viewWidth, viewHeight);
                draw(IDENTITY_MATRIX, createFloatBuffer(FULL_RECTANGLE_COORDS), 0, (FULL_RECTANGLE_COORDS.length / 2), 2, 2 * SIZEOF_FLOAT, mTmpMatrix,
                        createFloatBuffer(FULL_RECTANGLE_TEX_COORDS), mTextureId, 2 * SIZEOF_FLOAT);

                if(VERBOSE)Log.d(TAG, "Draw on screen...."+isRecording);
                //Calls eglSwapBuffers.  Use this to "publish" the current frame.
                EGL14.eglSwapBuffers(mEGLDisplay, eglSurface);

                if(isRecording) {
                    makeCurrent(encoderSurface);
                    if (VERBOSE) Log.d(TAG, "Made encoder surface current");
                    GLES20.glViewport(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);
                    draw(RECORD_IDENTITY_MATRIX, createFloatBuffer(FULL_RECTANGLE_COORDS), 0, (FULL_RECTANGLE_COORDS.length / 2), 2, 2 * SIZEOF_FLOAT, mTmpMatrix,
                            createFloatBuffer(FULL_RECTANGLE_TEX_COORDS), mTextureId, 2 * SIZEOF_FLOAT);
                    if (VERBOSE) Log.d(TAG, "Populated to encoder");
                    if (recordStop == -1) {
                        mediaRecorder.start();
                        recordStop = 1;
                    }
                    EGLExt.eglPresentationTimeANDROID(mEGLDisplay, encoderSurface, surfaceTexture.getTimestamp());
                    EGL14.eglSwapBuffers(mEGLDisplay, encoderSurface);
                }
            }
            frameCount++;
        }

        void shutdown()
        {
            Looper.myLooper().quit();
        }

        class CameraHandler extends Handler
        {
            WeakReference<CameraRenderer> cameraRender;

            public CameraHandler(CameraRenderer cameraRenderer){
                cameraRender = new WeakReference<>(cameraRenderer);
            }

            @Override
            public void handleMessage(Message msg) {
                CameraRenderer cameraRenderer = cameraRender.get();
                switch(msg.what)
                {
                    case SHUTDOWN:
                        Log.d(TAG,"Shutdown msg received");
                        cameraRenderer.shutdown();
                        break;
                    case FRAME_AVAILABLE:
                        if(VERBOSE)Log.d(TAG,"send to FRAME_AVAILABLE");
                        cameraRenderer.drawFrame();
                        if(VERBOSE)Log.d(TAG,"Record = "+isRecord);
                        if(isRecord){
                            if(VERBOSE)Log.d(TAG,"render frame = "+(++frameCnt));
                        }
                        break;
                    case RECORD_START:
                        isRecording = true;
                        setupMediaRecorder();
                        break;
                    case RECORD_STOP:
                        isRecording = false;
                        recordStop = -1;
                        mediaRecorder.stop();
                        mediaRecorder.release();
                        mediaRecorder = null;
                        Log.d(TAG,"stop isRecording == "+isRecording);
                        if(VERBOSE)Log.d(TAG, "Exit recording...");
                        Log.d(TAG,"Orig frame = "+frameCount+" , Rendered frame "+frameCnt);
                        break;
                }
            }
        }
    }
}
