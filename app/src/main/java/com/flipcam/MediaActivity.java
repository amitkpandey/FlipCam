package com.flipcam;

import android.app.Dialog;
import android.app.NotificationManager;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.content.FileProvider;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.CallbackManager;
import com.flipcam.constants.Constants;
import com.flipcam.data.MediaTableConstants;
import com.flipcam.media.FileMedia;
import com.flipcam.util.MediaUtil;
import com.flipcam.view.MediaFragment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import static com.flipcam.PermissionActivity.FC_MEDIA_PREFERENCE;

public class MediaActivity extends AppCompatActivity implements ViewPager.OnPageChangeListener{

    private static final String TAG = "MediaActivity";
    private ViewPager mPager;
    private PagerAdapter mPagerAdapter;
    FileMedia[] medias = null;
    LinearLayout videoControls;
    LinearLayout topMediaControls;
    String duration;
    TextView startTime;
    TextView endTime;
    SeekBar videoSeek;
    LinearLayout timeControls;
    LinearLayout parentMedia;
    HashMap<Integer,MediaFragment> hashMapFrags = new HashMap<>();
    ControlVisbilityPreference controlVisbilityPreference;
    ImageButton deleteMedia;
    Display display;
    Point screenSize;
    boolean isDelete = false;
    int previousSelectedFragment = 0;
    //Default to first fragment, if user did not scroll.
    int selectedPosition = 0;
    int deletePosition = -1;
    int itemCount = 0;
    ImageView noImage;
    TextView noImageText;
    ImageButton pause;
    ImageButton shareMedia;
    Dialog deleteAlert;
    Dialog shareAlert;
    Dialog noConnAlert;
    Dialog shareToFBAlert;
    Dialog logoutFB;
    Dialog permissionFB;
    Dialog appNotExist;
    CallbackManager callbackManager;
    NotificationManager mNotificationManager;
    Bitmap notifyIcon;
    Uri queueNotification;
    ImageView playCircle;
    View deleteMediaRoot;
    View taskInProgressRoot;
    LayoutInflater layoutInflater;
    Dialog taskAlert;
    View warningMsgRoot;
    Dialog warningMsg;
    IntentFilter mediaFilters;
    SharedPreferences sharedPreferences;
    SDCardEventReceiver sdCardEventReceiver;
    AppWidgetManager appWidgetManager;
    boolean VERBOSE = true;
    AudioManager audioManager;
    ImageView gridViewOn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(VERBOSE)Log.d(TAG,"onCreate");
        setContentView(R.layout.activity_media);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        WindowManager windowManager = (WindowManager)getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        display = windowManager.getDefaultDisplay();
        screenSize=new Point();
        display.getRealSize(screenSize);
        getSupportActionBar().hide();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        warningMsg = new Dialog(this);
        mediaFilters = new IntentFilter();
        sdCardEventReceiver = new SDCardEventReceiver();
        layoutInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        warningMsgRoot = layoutInflater.inflate(R.layout.warning_message, null);
        sharedPreferences = getSharedPreferences(Constants.FC_SETTINGS, Context.MODE_PRIVATE);
        videoControls = (LinearLayout)findViewById(R.id.videoControls);
        if(!sharedPreferences.getBoolean(Constants.SAVE_MEDIA_PHONE_MEM, true)){
            if(doesSDCardExist() == null){
                exitToPreviousActivity();
                return;
            }
            else {
                medias = MediaUtil.getMediaList(getApplicationContext());
            }
        }
        else {
            medias = MediaUtil.getMediaList(getApplicationContext());
        }
        mPager = (ViewPager) findViewById(R.id.mediaPager);
        mPager.setOffscreenPageLimit(1);
        mPagerAdapter = new MediaSlidePager(getSupportFragmentManager());
        mPager.setAdapter(mPagerAdapter);
        if(getIntent().getExtras().getBoolean("fromGallery")) {
            int mediaPos = getIntent().getExtras().getInt("mediaPosition");
            if(VERBOSE)Log.d(TAG, "Intent extra = " +mediaPos);
            mPager.setCurrentItem(mediaPos);
            selectedPosition = previousSelectedFragment = mediaPos;
        }
        else if(getIntent().getExtras().getBoolean("fromMenu")){
            int scrollPos = getIntent().getExtras().getInt("scrollTo");
            Log.d(TAG, "go to page = "+scrollPos);
            mPager.setCurrentItem(scrollPos);
            selectedPosition = previousSelectedFragment = scrollPos;
        }
        deleteMedia = (ImageButton)findViewById(R.id.deleteMedia);
        deleteAlert = new Dialog(this);
        deleteMedia.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                medias = MediaUtil.getMediaList(getApplicationContext());
                if(medias != null) {
                    if(VERBOSE)Log.d(TAG, "Delete position = " + selectedPosition);
                    TextView deleteMsg = (TextView) deleteMediaRoot.findViewById(R.id.deleteMsg);
                    if (isImage(medias[selectedPosition].getPath())) {
                        deleteMsg.setText(getResources().getString(R.string.deleteMessage, getResources().getString(R.string.photo)));
                    } else {
                        deleteMsg.setText(getResources().getString(R.string.deleteMessage, getResources().getString(R.string.video)));
                    }
                    deleteAlert.setContentView(deleteMediaRoot);
                    deleteAlert.setCancelable(true);
                    deleteAlert.show();
                }
            }
        });
        noConnAlert = new Dialog(this);
        pause = (ImageButton) findViewById(R.id.playButton);
        shareMedia = (ImageButton)findViewById(R.id.shareMedia);
        shareToFBAlert = new Dialog(this);
        shareAlert = new Dialog(this);
        logoutFB = new Dialog(this);
        permissionFB = new Dialog(this);
        appNotExist = new Dialog(this);
        shareMedia.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                medias = MediaUtil.getMediaList(getApplicationContext());
                if(medias != null) {
                    if(VERBOSE)Log.d(TAG, "Share position = " + selectedPosition);
                    Uri mediaUri;
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                        if(VERBOSE)Log.d(TAG, "For OREO use FileProvider");
                        mediaUri = FileProvider.getUriForFile(MediaActivity.this, BuildConfig.APPLICATION_ID+".provider",
                                new File(medias[selectedPosition].getPath()));
                    }
                    else {
                        mediaUri = Uri.fromFile(new File(medias[selectedPosition].getPath()));
                    }
                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, mediaUri);
                    if (isImage(medias[selectedPosition].getPath())) {
                        shareIntent.setType("image/jpeg");
                    } else {
                        shareIntent.setType("video/mp4");
                    }
                    if (doesAppExistForIntent(shareIntent)) {
                        if(VERBOSE)Log.d(TAG, "Apps exists");
                        Intent chooser;
                        if (isImage(medias[selectedPosition].getPath())) {
                            chooser = Intent.createChooser(shareIntent, getResources().getString(R.string.chooserTitleImage));
                        } else {
                            chooser = Intent.createChooser(shareIntent, getResources().getString(R.string.chooserTitleVideo));
                        }
                        if (shareIntent.resolveActivity(getPackageManager()) != null) {
                            if(VERBOSE)Log.d(TAG, "Start activity to choose");
                            if(!sharedPreferences.getBoolean(Constants.SAVE_MEDIA_PHONE_MEM, true)){
                                if(doesSDCardExist() == null){
                                    exitToPreviousActivity();
                                    return;
                                }
                            }
                            startActivity(chooser);
                        }
                    }
                }
            }
        });
        startTime = (TextView)findViewById(R.id.startTime);
        endTime = (TextView)findViewById(R.id.endTime);
        videoSeek = (SeekBar)findViewById(R.id.videoSeek);
        topMediaControls = (LinearLayout)findViewById(R.id.topMediaControls);
        timeControls = (LinearLayout)findViewById(R.id.timeControls);
        parentMedia = (LinearLayout)findViewById(R.id.parentMedia);
        controlVisbilityPreference = (ControlVisbilityPreference)getApplicationContext();
        noImage = (ImageView)findViewById(R.id.noImage);
        noImageText = (TextView)findViewById(R.id.noImageText);
        playCircle = (ImageView)findViewById(R.id.playVideo);
        gridViewOn = (ImageView)findViewById(R.id.gridViewOn);
        if(VERBOSE)Log.d(TAG, "savedInstanceState = "+savedInstanceState);
        if(savedInstanceState == null){
            clearMediaPreferences();
            controlVisbilityPreference.setHideControl(true);
            reDrawPause();
            reDrawTopMediaControls();
            if(isImage(medias[0].getPath())) {
                if(VERBOSE)Log.d(TAG, "Hide PlayForVideo");
                removeVideoControls();
                hidePlayForVideo();
            }
            else{
                if(!controlVisbilityPreference.isHideControl()) {
                    if(VERBOSE)Log.d(TAG, "Show PlayForVideo");
                    setupPlayForVideo(0);
                    showPlayForVideo();
                }
            }
        }
        else{
            selectedPosition = savedInstanceState.getInt("selectedPosition");
            if(VERBOSE)Log.d(TAG, "get selectedPosition = "+selectedPosition);
            if(isImage(medias[selectedPosition].getPath())) {
                removeVideoControls();
                hidePlayForVideo();
            }
            else{
                if(!controlVisbilityPreference.isHideControl()) {
                    setupPlayForVideo(0);
                    showPlayForVideo();
                }
            }
        }
        gridViewOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent mediaGridAct = new Intent(getApplicationContext(), GalleryActivity.class);
                mediaGridAct.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(mediaGridAct);
                finish();
            }
        });
        notifyIcon = BitmapFactory.decodeResource(getApplicationContext().getResources(),R.drawable.ic_launcher);
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        queueNotification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        layoutInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        deleteMediaRoot = layoutInflater.inflate(R.layout.delete_media, null);
        taskInProgressRoot = layoutInflater.inflate(R.layout.task_in_progress, null);
        taskAlert = new Dialog(this);
        appWidgetManager = (AppWidgetManager)getSystemService(Context.APPWIDGET_SERVICE);
    }

    AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener(){
        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.d(TAG, "onAudioFocusChange = "+focusChange);
            switch(focusChange){
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Build.VERSION.SDK_INT < Build.VERSION_CODES.M){
                        Log.d(TAG, "setStreamMute");
                        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
                    }
                    else{
                        Log.d(TAG, "adjustStreamVolume");
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    audioManager.abandonAudioFocus(this);
                    break;
            }
        }
    };

    @Override
    protected void onStop() {
        super.onStop();
        if(VERBOSE)Log.d(TAG,"onStop");
        if(medias != null) {
            SharedPreferences.Editor mediaState = sharedPreferences.edit();
            if(sharedPreferences.getBoolean(Constants.SAVE_MEDIA_PHONE_MEM, true)) {
                mediaState.putInt(Constants.MEDIA_COUNT_MEM, medias.length);
            }
            else{
                mediaState.putInt(Constants.MEDIA_COUNT_SD_CARD, medias.length);
            }
            mediaState.commit();
            if(VERBOSE)Log.d(TAG, "Media length before leaving = " + medias.length);
        }
        else{
            clearMediaPreferences();
        }
        if(VERBOSE)Log.d(TAG ,"selectedPosition = "+selectedPosition);
        if(hashMapFrags.get(selectedPosition) != null) {
            hashMapFrags.get(selectedPosition).getMediaView().setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        if(VERBOSE)Log.d(TAG,"onDestroy");
        super.onDestroy();
    }

    public void reDrawPause(){
        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if(display.getRotation() == Surface.ROTATION_0) {
            pauseParams.weight = 0.1f;
        }
        else{
            pauseParams.weight = 0.04f;
        }
        pauseParams.height = (int)getResources().getDimension(R.dimen.playButtonHeight);
        pauseParams.gravity = Gravity.CENTER;
        pause.setScaleType(ImageView.ScaleType.CENTER_CROP);
        pause.setLayoutParams(pauseParams);
    }

    void exitToPreviousActivity(){
        if(getIntent().getExtras().getBoolean("fromGallery")) {
            exitMediaAndShowNoSDCardInGallery();
        }
        else {
            exitMediaAndShowNoSDCard();
        }
    }

    public void reDrawTopMediaControls(){
        FrameLayout.LayoutParams topMediaParams = new FrameLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if(display.getRotation() == Surface.ROTATION_0){
            topMediaParams.height = (int) getResources().getDimension(R.dimen.topMediaBarPortrait);
            deleteMedia.setImageDrawable(getResources().getDrawable(R.drawable.ic_delete_portrait));
            shareMedia.setImageDrawable(getResources().getDrawable(R.drawable.ic_share_portrait));
        }
        else{
            topMediaParams.height = (int) getResources().getDimension(R.dimen.topMediaBarLandscape);
            deleteMedia.setImageDrawable(getResources().getDrawable(R.drawable.ic_delete));
            shareMedia.setImageDrawable(getResources().getDrawable(R.drawable.ic_share));
        }
        topMediaControls.setLayoutParams(topMediaParams);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if(VERBOSE)Log.d(TAG,"onConfigurationChanged = "+display.getRotation());
        reDrawPause();
        reDrawTopMediaControls();
    }

    public String doesSDCardExist(){
        String sdcardpath = sharedPreferences.getString(Constants.SD_CARD_PATH, "");
        try {
            String filename = "/doesSDCardExist_"+String.valueOf(System.currentTimeMillis()).substring(0,5);
            sdcardpath += filename;
            final String sdCardFilePath = sdcardpath;
            final FileOutputStream createTestFile = new FileOutputStream(sdcardpath);
            if(VERBOSE)Log.d(TAG, "Able to create file... SD Card exists");
            File testfile = new File(sdCardFilePath);
            testfile.delete();
            createTestFile.close();
        } catch (FileNotFoundException e) {
            if(VERBOSE)Log.d(TAG, "Unable to create file... SD Card NOT exists..... "+e.getMessage());
            return null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sharedPreferences.getString(Constants.SD_CARD_PATH, "");
    }

    public void deleteMedia(int position)
    {
        if(!sharedPreferences.getBoolean(Constants.SAVE_MEDIA_PHONE_MEM, true)){
            if(doesSDCardExist() == null){
                taskAlert.dismiss();
                exitToPreviousActivity();
                return;
            }
        }
        if(VERBOSE)Log.d(TAG,"Length before delete = "+medias.length);
        if(VERBOSE)Log.d(TAG,"Deleting file = "+medias[position].getPath());
        String deletePath = medias[position].getPath();
        if(MediaUtil.deleteFile(medias[position])) {
            if(VERBOSE)Log.d(TAG, "deletePath = "+deletePath);
            getContentResolver().delete(Uri.parse(MediaTableConstants.BASE_CONTENT_URI + "/deleteMedia"), null, new String[]{deletePath});
            itemCount = 0;
            isDelete = true;
            if(position == medias.length - 1){
                //onPageSelected is called when deleting last media. Need to make previousSelectedFragment as -1.
                previousSelectedFragment = -1;
            }
            medias = MediaUtil.getMediaList(getApplicationContext());
            if(medias != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(VERBOSE)Log.d(TAG, "BEFORE notifyDataSetChanged");
                        mPagerAdapter.notifyDataSetChanged();
                        if(VERBOSE)Log.d(TAG, "AFTER notifyDataSetChanged");
                        taskAlert.dismiss();
                    }
                });
            }
            else{
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        taskAlert.dismiss();
                        showNoImagePlaceholder();
                    }
                });
            }
        }
        else{
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    taskAlert.dismiss();
                    Toast.makeText(getApplicationContext(),getResources().getString(R.string.unableToDelete),Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(VERBOSE)Log.d(TAG, "onSaveInstanceState = "+selectedPosition);
        outState.putInt("selectedPosition",selectedPosition);
    }

    public void setupPlayForVideo(final int videoPos){
        if(VERBOSE)Log.d(TAG, "setupPlayForVideo");
        playCircle.setClickable(true);
        playCircle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MediaFragment currentFrag = hashMapFrags.get(videoPos);
                startPlayingMedia(currentFrag, false);
            }
        });
    }

    private void startPlayingMedia(MediaFragment currentFrag, boolean fromPauseBtn){
        int audioFocus = audioManager.requestAudioFocus(onAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if(audioFocus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            if (currentFrag.isPlayCompleted()) {
                currentFrag.setIsPlayCompleted(false);
            }
            if(!fromPauseBtn) {
                if (VERBOSE) Log.d(TAG, "Set PLAY using Circle");
            }
            else{
                if(VERBOSE)Log.d(TAG,"Set PLAY");
            }
            currentFrag.playInProgress = true;
            if (VERBOSE)
                Log.d(TAG, "Duration of video = " + currentFrag.mediaPlayer.getDuration() + " , path = " +
                        currentFrag.path.substring(currentFrag.path.lastIndexOf("/"), currentFrag.path.length()));
            currentFrag.mediaPlayer.start();
            if (!fromPauseBtn) {
                pause.setVisibility(View.VISIBLE);
            }
            pause.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause));
            currentFrag.play = true;
            playCircle.setVisibility(View.GONE);
        }
    }

    public void showPlayForVideo(){
        playCircle.setVisibility(View.VISIBLE);
    }

    public void hidePlayForVideo(){
        playCircle.setVisibility(View.GONE);
    }

    public boolean doesAppExistForIntent(Intent shareIntent){
        PackageManager packageManager = getPackageManager();
        List activities = packageManager.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if(VERBOSE)Log.d(TAG, "No of activities that can share = "+activities.size());
        boolean isIntentSafe = activities.size() > 0;
        return isIntentSafe;
    }

    public void okToClose(View view){
        noConnAlert.dismiss();
    }

    public void delete(View view){
        if(VERBOSE)Log.d(TAG,"DELETE");
        deleteAlert.dismiss();
        TextView savetocloudtitle = (TextView)taskInProgressRoot.findViewById(R.id.savetocloudtitle);
        TextView signInText = (TextView)taskInProgressRoot.findViewById(R.id.signInText);
        ImageView signInImage = (ImageView)taskInProgressRoot.findViewById(R.id.signInImage);
        signInImage.setVisibility(View.INVISIBLE);
        signInText.setText(getResources().getString(R.string.deleteMediaMsg));
        savetocloudtitle.setText(getResources().getString(R.string.deleteTitle));
        taskInProgressRoot.setBackgroundColor(getResources().getColor(R.color.mediaControlColor));
        taskAlert.setContentView(taskInProgressRoot);
        taskAlert.setCancelable(false);
        taskAlert.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                deleteMedia(selectedPosition);
            }
        }).start();
    }

    public void cancel(View view){
        if(VERBOSE)Log.d(TAG,"CANCEL");
        deleteAlert.dismiss();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callbackManager.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    private void clearMediaPreferences(){
        SharedPreferences mediaValues = getSharedPreferences(FC_MEDIA_PREFERENCE,Context.MODE_PRIVATE);
        SharedPreferences.Editor mediaState = null;
        if(mediaValues!=null){
            mediaState = mediaValues.edit();
            if(mediaState!=null){
                mediaState.clear();
                mediaState.commit();
                if(VERBOSE)Log.d(TAG,"CLEAR ALL");
            }
        }
    }

    @Override
    public void onPageSelected(int position) {
        if(!sharedPreferences.getBoolean(Constants.SAVE_MEDIA_PHONE_MEM, true)){
            String sdCard = doesSDCardExist();
            if(sdCard == null){
                exitToPreviousActivity();
                return;
            }
        }
        if(VERBOSE)Log.d(TAG,"onPageSelected = "+position+", previousSelectedFragment = "+previousSelectedFragment);
        selectedPosition = position;
        final MediaFragment currentFrag = hashMapFrags.get(position);
        if(VERBOSE)Log.d(TAG,"isHideControl = "+controlVisbilityPreference.isHideControl());
        //Reset preferences for every new fragment to be displayed.
        clearMediaPreferences();
        if(previousSelectedFragment != -1) {
            MediaFragment previousFragment = hashMapFrags.get(previousSelectedFragment);
            //If previous fragment had a video, stop the video and tracker thread immediately.
            if(VERBOSE)Log.d(TAG, "medias length = "+medias.length);
            if (!isImage(medias[previousSelectedFragment].getPath())) {
                if(VERBOSE)Log.d(TAG, "Stop previous tracker thread = " + previousFragment.path);
                previousFragment.stopTrackerThread();
                if (previousFragment.mediaPlayer.isPlaying()) {
                    if(VERBOSE)Log.d(TAG, "Pause previous playback");
                    previousFragment.mediaPlayer.pause();
                }
            }
        }
        //Display controls based on image/video
        if(isImage(medias[position].getPath())){
            if(VERBOSE)Log.d(TAG,"HIDE VIDEO");
            hidePlayForVideo();
            removeVideoControls();
        }
        else{
            if(controlVisbilityPreference.isHideControl()) {
                if(VERBOSE)Log.d(TAG,"show controls");
                showControls();
            }
            else{
                if(VERBOSE)Log.d(TAG,"hide controls");
                removeVideoControls();
            }
            setupVideo(currentFrag,position);
            currentFrag.previousPos = 0;
            if(VERBOSE)Log.d(TAG,"Has VIDEO TRACKER STARTED? = "+currentFrag.isStartTracker());
            if(!currentFrag.isStartTracker()){
                currentFrag.startTrackerThread();
            }
        }
        previousSelectedFragment = position;
    }

    public void showControls(){
        pause.setVisibility(View.VISIBLE);
        startTime.setVisibility(View.VISIBLE);
        endTime.setVisibility(View.VISIBLE);
        videoSeek.setVisibility(View.VISIBLE);
    }

    public void removeVideoControls(){
        pause.setVisibility(View.INVISIBLE);
        startTime.setVisibility(View.INVISIBLE);
        endTime.setVisibility(View.INVISIBLE);
        videoSeek.setVisibility(View.INVISIBLE);
    }

    public void setupVideoControls(final int position){
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        mediaMetadataRetriever.setDataSource(medias[position].getPath());
        duration = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        calculateAndDisplayEndTime(Integer.parseInt(duration), true, position);
        if(VERBOSE)Log.d(TAG,"Set MEDIA = "+medias[position].getPath());
        //Include tracker and reset position to start playing from start.
        videoControls.removeAllViews();
        videoControls.addView(timeControls);
        videoControls.addView(videoSeek);
        videoControls.addView(parentMedia);
        videoSeek.setMax(Integer.parseInt(duration));
        videoSeek.setProgress(0);
        //videoSeek.setThumb(getResources().getDrawable(R.drawable.turqoise));
        videoSeek.setProgressTintList(ColorStateList.valueOf(getResources().getColor(R.color.turqoise)));
        videoSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser) {
                    videoSeek.setProgress(progress);
                    hashMapFrags.get(position).mediaPlayer.seekTo(progress);
                    calculateAndDisplayEndTime(progress, false, position);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if(hashMapFrags.get(position).mediaPlayer.isPlaying()){
                    hashMapFrags.get(position).mediaPlayer.pause();
                    wasPlaying = true;
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(wasPlaying){
                    hashMapFrags.get(position).mediaPlayer.start();
                    wasPlaying = false;
                }
            }
        });
    }

    boolean wasPlaying = false;

    public void setupVideo(final MediaFragment currentFrag, int position){
        setupVideoControls(position);
        currentFrag.play=false;
        currentFrag.playInProgress=false;
        getIntent().removeExtra("saveVideoForMinimize");
        currentFrag.savedVideo = null;
        currentFrag.setIsPlayCompleted(false);
        final int pos = position;
        currentFrag.mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                if(VERBOSE)Log.d(TAG,"CATCH onError = "+extra);
                if(extra == MediaPlayer.MEDIA_ERROR_IO){
                    //Possible file not found since SD Card removed
                    if(!sharedPreferences.getBoolean(Constants.SAVE_MEDIA_PHONE_MEM, true)){
                        exitMediaAndShowNoSDCard();
                        return true;
                    }
                }
                currentFrag.mediaPlayer.reset();
                try {
                    currentFrag.mediaPlayer.setOnCompletionListener(currentFrag);
                    currentFrag.mediaPlayer.setOnPreparedListener(currentFrag);
                    currentFrag.mediaPlayer.setOnErrorListener(currentFrag);
                    currentFrag.mediaPlayer.setDataSource("file://"+medias[pos].getPath());
                    currentFrag.mediaPlayer.prepare();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
        currentFrag.resetMediaPlayer();
        currentFrag.resetVideoTime();
        reDrawPause();
        pause.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_arrow));
        pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!currentFrag.play) {
                    startPlayingMedia(currentFrag, true);
                } else {
                    if(VERBOSE)Log.d(TAG,"Set PAUSE");
                    currentFrag.mediaPlayer.pause();
                    pause.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_arrow));
                    currentFrag.play = false;
                }
            }
        });
        setupPlayForVideo(position);
        if(!controlVisbilityPreference.isHideControl()) {
            showPlayForVideo();
        }
        else{
            hidePlayForVideo();
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    public void calculateAndDisplayEndTime(int latestPos, boolean eTime, int videoPos)
    {
        int videoLength = latestPos;
        int secs = (videoLength / 1000);
        int hour = 0;
        int mins = 0;
        if(secs >= 60){
            mins = secs / 60;
            if(mins >= 60){
                hour = mins / 60;
                mins = mins % 60;
            }
            secs = secs % 60;
        }
        String showSec = "0";
        String showMin = "0";
        String showHr = "0";
        if(secs < 10){
            showSec += secs;
        }
        else{
            showSec = secs+"";
        }

        if(mins < 10){
            showMin += mins;
        }
        else{
            showMin = mins+"";
        }

        if(hour < 10){
            showHr += hour;
        }
        else{
            showHr = hour+"";
        }
        if(eTime) {
            startTime.setText(getResources().getString(R.string.START_TIME));
            endTime.setText(showHr + " : " + showMin + " : " + showSec);
        }
        else{
            hashMapFrags.get(videoPos).setSeconds(secs);
            hashMapFrags.get(videoPos).setMinutes(mins);
            hashMapFrags.get(videoPos).setHours(hour);
            startTime.setText(showHr + " : " + showMin + " : " + showSec);
        }
    }

    class SDCardEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if(VERBOSE)Log.d(TAG, "onReceive = "+intent.getAction());
            if(intent.getAction().equalsIgnoreCase(Intent.ACTION_MEDIA_UNMOUNTED)){
                //Check if SD Card was selected
                if(!sharedPreferences.getBoolean(Constants.SAVE_MEDIA_PHONE_MEM, true)){
                    exitMediaAndShowNoSDCard();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(VERBOSE)Log.d(TAG,"onResume");
        registerReceiver(sdCardEventReceiver, mediaFilters);
        mPager.addOnPageChangeListener(this);
        mediaFilters.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        mediaFilters.addDataScheme("file");
        if (!sharedPreferences.getBoolean(Constants.SAVE_MEDIA_PHONE_MEM, true)) {
            if (doesSDCardExist() == null) {
                exitToPreviousActivity();
                return;
            } else {
                refreshMediaFromSource();
            }
        } else {
            refreshMediaFromSource();
        }
    }

    public void refreshMediaFromSource(){
        itemCount = 0;
        int oldLength;
        if(sharedPreferences.getBoolean(Constants.SAVE_MEDIA_PHONE_MEM, true)) {
            oldLength = getSharedPreferences(FC_MEDIA_PREFERENCE, Context.MODE_PRIVATE).getInt(Constants.MEDIA_COUNT_MEM, 0);
        }
        else {
            oldLength = getSharedPreferences(FC_MEDIA_PREFERENCE, Context.MODE_PRIVATE).getInt(Constants.MEDIA_COUNT_SD_CARD, 0);
        }
        medias = MediaUtil.getMediaList(getApplicationContext());
        if(medias != null) {
            if (medias.length < oldLength) {
                if(VERBOSE)Log.d(TAG, "Possible deletions outside of App");
                isDelete = true;
                previousSelectedFragment = -1;
            } else {
                if(VERBOSE)Log.d(TAG, "Files added or no change");
            }
            hideNoImagePlaceholder();
            mPagerAdapter.notifyDataSetChanged();
        }
        else{
            clearMediaPreferences();
            showNoImagePlaceholder();
        }
    }

    public void exitMediaAndShowNoSDCard(){
        if(VERBOSE)Log.d(TAG, "exitMediaAndShowNoSDCard");
        Intent camera = new Intent(this,CameraActivity.class);
        camera.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(camera);
        finish();
    }

    void exitMediaAndShowNoSDCardInGallery(){
        if(VERBOSE)Log.d(TAG, "exitMediaAndShowNoSDCardInGallery");
        Intent mediaGrid = new Intent(this,GalleryActivity.class);
        mediaGrid.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(mediaGrid);
        finish();
    }

    public void hideNoImagePlaceholder(){
        //topMediaControls.setVisibility(View.VISIBLE);
        parentMedia.setVisibility(View.VISIBLE);
        mPager.setVisibility(View.VISIBLE);
        noImage.setVisibility(View.GONE);
        noImageText.setVisibility(View.GONE);
    }

    public void showNoImagePlaceholder(){
        //No Images
        //topMediaControls.setVisibility(View.GONE);

        videoControls.setVisibility(View.GONE);
        mPager.setVisibility(View.GONE);
        noImage.setVisibility(View.VISIBLE);
        noImageText.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPager.removeOnPageChangeListener(this);
        unregisterReceiver(sdCardEventReceiver);
        if(VERBOSE)Log.d(TAG,"onPause");
        if(getIntent().getExtras().getBoolean("fromGallery")) {
            controlVisbilityPreference.setMediaSelectedPosition(selectedPosition);
        }
    }

    public boolean isImage(String path)
    {
        if(path.endsWith(getResources().getString(R.string.IMG_EXT)) || path.endsWith(getResources().getString(R.string.ANOTHER_IMG_EXT))){
            return true;
        }
        return false;
    }

    class MediaSlidePager extends FragmentStatePagerAdapter
    {
        @Override
        public int getCount() {
            return medias.length;
        }

        @Override
        public Fragment getItem(int position) {
            if(VERBOSE)Log.d(TAG,"getItem = "+position);
            MediaFragment mediaFragment;
            if(isDelete) {
                isDelete = false;
                mediaFragment = MediaFragment.newInstance(position, true);
                if(mediaFragment.getUserVisibleHint()) {
                    if (isImage(medias[position].getPath())) {
                        if(VERBOSE)Log.d(TAG, "IS image");
                        removeVideoControls();
                    } else {
                        if(VERBOSE)Log.d(TAG, "IS video");
                        showControls();
                        setupVideoControls(position);
                    }
                }
            }
            else{
                mediaFragment = MediaFragment.newInstance(position, false);
            }
            hashMapFrags.put(Integer.valueOf(position),mediaFragment);
            return mediaFragment;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            super.destroyItem(container, position, object);
            hashMapFrags.remove(position);
        }

        public MediaSlidePager(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getItemPosition(Object object) {
            MediaFragment fragment = (MediaFragment)object;
            if(VERBOSE)Log.d(TAG,"getItemPos = "+fragment.getPath()+", POS = "+fragment.getFramePosition()+", Uservisible? = "+fragment.getUserVisibleHint());
            itemCount++;
            if(MediaUtil.doesPathExist(fragment.getPath())){
                if(deletePosition != -1) {
                    if (deletePosition < medias.length) {
                        if(fragment.getFramePosition() == (deletePosition + 1) || fragment.getFramePosition() == (deletePosition + 2)) {
                            if(VERBOSE)Log.d(TAG, "Recreate the next fragment as well");
                            if(itemCount == 3) {
                                deletePosition = -1;
                            }
                        }
                        return POSITION_NONE;
                    } else if (deletePosition == medias.length - 1 && fragment.getFramePosition() == (deletePosition - 1)) {
                        if(VERBOSE)Log.d(TAG, "Recreate the previous fragment as well");
                        deletePosition = -1;
                        return POSITION_NONE;
                    }
                }
                return POSITION_UNCHANGED;
            }
            else {
                deletePosition = fragment.getFramePosition();
                return POSITION_NONE;
            }
        }
    }
}
