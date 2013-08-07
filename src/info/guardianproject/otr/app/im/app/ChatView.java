/*
 * Copyright (C) 2008 Esmertec AG. Copyright (C) 2008 The Android Open Source
 * Project
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package info.guardianproject.otr.app.im.app;

import info.guardianproject.emoji.EmojiGroup;
import info.guardianproject.emoji.EmojiManager;
import info.guardianproject.emoji.EmojiPagerAdapter;
import info.guardianproject.otr.IOtrChatSession;
import info.guardianproject.otr.IOtrKeyManager;
import info.guardianproject.otr.app.im.IChatListener;
import info.guardianproject.otr.app.im.IChatSession;
import info.guardianproject.otr.app.im.IChatSessionListener;
import info.guardianproject.otr.app.im.IChatSessionManager;
import info.guardianproject.otr.app.im.IContactList;
import info.guardianproject.otr.app.im.IContactListListener;
import info.guardianproject.otr.app.im.IContactListManager;
import info.guardianproject.otr.app.im.IImConnection;
import info.guardianproject.otr.app.im.R;
import info.guardianproject.otr.app.im.app.MessageView.DeliveryState;
import info.guardianproject.otr.app.im.app.MessageView.EncryptionState;
import info.guardianproject.otr.app.im.app.adapter.ChatListenerAdapter;
import info.guardianproject.otr.app.im.app.adapter.ChatSessionListenerAdapter;
import info.guardianproject.otr.app.im.engine.Contact;
import info.guardianproject.otr.app.im.engine.ImConnection;
import info.guardianproject.otr.app.im.engine.ImErrorInfo;
import info.guardianproject.otr.app.im.provider.Imps;
import info.guardianproject.otr.app.im.provider.ImpsAddressUtils;
import info.guardianproject.otr.app.im.service.ImServiceConstants;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import net.java.otr4j.session.SessionStatus;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.DataSetObserver;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Browser;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonSyntaxException;

public class ChatView extends LinearLayout {
    // This projection and index are set for the query of active chats
    static final String[] CHAT_PROJECTION = { Imps.Contacts._ID, Imps.Contacts.ACCOUNT,
                                             Imps.Contacts.PROVIDER, Imps.Contacts.USERNAME,
                                             Imps.Contacts.NICKNAME, Imps.Contacts.TYPE,
                                             Imps.Presence.PRESENCE_STATUS,
                                             Imps.Chats.LAST_UNREAD_MESSAGE, 
                                             Imps.Chats._ID
    };
    
    static final int CONTACT_ID_COLUMN = 0;
    static final int ACCOUNT_COLUMN = 1;
    static final int PROVIDER_COLUMN = 2;
    static final int USERNAME_COLUMN = 3;
    static final int NICKNAME_COLUMN = 4;
    static final int TYPE_COLUMN = 5;
    static final int PRESENCE_STATUS_COLUMN = 6;
    static final int LAST_UNREAD_MESSAGE_COLUMN = 7;
    static final int CHAT_ID_COLUMN = 8;

    static final String[] INVITATION_PROJECT = { Imps.Invitation._ID, Imps.Invitation.PROVIDER,
                                                Imps.Invitation.SENDER, };
    static final int INVITATION_ID_COLUMN = 0;
    static final int INVITATION_PROVIDER_COLUMN = 1;
    static final int INVITATION_SENDER_COLUMN = 2;

    static final StyleSpan STYLE_BOLD = new StyleSpan(Typeface.BOLD);
    static final StyleSpan STYLE_NORMAL = new StyleSpan(Typeface.NORMAL);

    Markup mMarkup;

    NewChatActivity mActivity;
    ImApp mApp;
    SimpleAlertHandler mHandler;
    Cursor mCursor;

    //private ImageView mStatusIcon;
   // private TextView mTitle;
    /*package*/ListView mHistory;
    EditText mComposeMessage;
    private ImageButton mSendButton;
    private View mStatusWarningView;
    private ImageView mWarningIcon;
    private TextView mWarningText;
    
    private ViewPager mEmojiPager;

    private ImageView mDeliveryIcon;
    private boolean mExpectingDelivery;
    private CompoundButton mOtrSwitch;
    private MessageAdapter mMessageAdapter;
    private IChatSessionManager mChatSessionManager;
    private IChatSessionListener mChatSessionListener;

    private IChatSession mCurrentChatSession;
    private IOtrKeyManager mOtrKeyManager;
    private IOtrChatSession mOtrChatSession;

    long mLastChatId=-1;
    int mType;
    String mNickName;
    String mUserName;
    long mProviderId;
    long mAccountId;
    long mInvitationId;
    private Context mContext; // TODO
    private int mPresenceStatus;

    private int mViewType;

    private static final int VIEW_TYPE_CHAT = 1;
    private static final int VIEW_TYPE_INVITATION = 2;
    private static final int VIEW_TYPE_SUBSCRIPTION = 3;

    private static final long SHOW_TIME_STAMP_INTERVAL = 30 * 1000; // 1 minute
    private static final long SHOW_DELIVERY_INTERVAL = 5 * 1000; // 10 seconds
    private static final long DEFAULT_QUERY_INTERVAL = 1000;
    private static final int QUERY_TOKEN = 10;

    // Async QueryHandler
    private final class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(Context context) {
            super(context.getContentResolver());
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor c) {
            mExpectingDelivery = false;
            setDeliveryIcon();
            
            if (c != null)
            {
                Cursor cursor = new DeltaCursor(c);
    
                if (Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
                    log("onQueryComplete: cursor.count=" + cursor.getCount());
                }
    
                if (mMessageAdapter != null && cursor != null)
                    mMessageAdapter.changeCursor(cursor);
            }
        }
    }

    private QueryHandler mQueryHandler;

    public SimpleAlertHandler getHandler() {
        return mHandler;
    }

    public int getType() {
        return mViewType;
    }

    private class RequeryCallback implements Runnable {
        public void run() {
            if (Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
                log("RequeryCallback");
            }
            requeryCursor();
        }
    }

    private RequeryCallback mRequeryCallback = null;

    private OnItemClickListener mOnItemClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (!(view instanceof MessageView)) {
                return;
            }
            
            URLSpan[] links = ((MessageView) view).getMessageLinks();
            if (links.length > 0) {
                

                final ArrayList<String> linkUrls = new ArrayList<String>(links.length);
                for (URLSpan u : links) {
                    linkUrls.add(u.getURL());
                }
                ArrayAdapter<String> a = new ArrayAdapter<String>(mActivity,
                        android.R.layout.select_dialog_item, linkUrls);
                AlertDialog.Builder b = new AlertDialog.Builder(mActivity);
                b.setTitle(R.string.select_link_title);
                b.setCancelable(true);
                b.setAdapter(a, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Uri uri = Uri.parse(linkUrls.get(which));
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        intent.putExtra(ImServiceConstants.EXTRA_INTENT_PROVIDER_ID, mProviderId);
                        intent.putExtra(ImServiceConstants.EXTRA_INTENT_ACCOUNT_ID, mAccountId);
                        intent.putExtra(Browser.EXTRA_APPLICATION_ID, mActivity.getPackageName());
                        mActivity.startActivity(intent);
                    }
                });
                b.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                b.show();
            }
            else
            {
                viewProfile();
            }
        }
    };

    private IChatListener mChatListener = new ChatListenerAdapter() {
        @Override
        public void onIncomingMessage(IChatSession ses,
                info.guardianproject.otr.app.im.engine.Message msg) {
            scheduleRequery(DEFAULT_QUERY_INTERVAL);

        }

        @Override
        public void onContactJoined(IChatSession ses, Contact contact) {
            scheduleRequery(DEFAULT_QUERY_INTERVAL);
        }

        @Override
        public void onContactLeft(IChatSession ses, Contact contact) {
            scheduleRequery(DEFAULT_QUERY_INTERVAL);
        }

        @Override
        public void onSendMessageError(IChatSession ses,
                info.guardianproject.otr.app.im.engine.Message msg, ImErrorInfo error) {
            scheduleRequery(DEFAULT_QUERY_INTERVAL);
        }

        public void onIncomingReceipt(IChatSession ses, String packetId) throws RemoteException {
            scheduleRequery(DEFAULT_QUERY_INTERVAL);
        }

        public void onStatusChanged(IChatSession ses) throws RemoteException {
            scheduleRequery(DEFAULT_QUERY_INTERVAL);
         
            
        };
    };


    private Runnable mUpdateChatCallback = new Runnable() {
        public void run() {
            if (mCursor.requery() && mCursor.moveToFirst()) {
                updateChat();
            }
        }
    };
    
    private IContactListListener mContactListListener = new IContactListListener.Stub() {
        public void onAllContactListsLoaded() {
        }

        public void onContactChange(int type, IContactList list, Contact contact) {
        }

        public void onContactError(int errorType, ImErrorInfo error, String listName,
                Contact contact) {
        }

        public void onContactsPresenceUpdate(Contact[] contacts) {
            if (Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
                log("onContactsPresenceUpdate()");
            }
            for (Contact c : contacts) {
                if (c.getAddress().getAddress().equals(mUserName)) {
                    mHandler.post(mUpdateChatCallback);
                    scheduleRequery(DEFAULT_QUERY_INTERVAL);
                    break;
                }
            }
        }
    };

    static final void log(String msg) {
        Log.d(ImApp.LOG_TAG, "<ChatView> " + msg);
    }

    public ChatView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mActivity = (NewChatActivity) context;
        mApp = (ImApp)mActivity.getApplication();
        mHandler = new ChatViewHandler(mActivity);
        mContext = context;

    }

    void registerForConnEvents() {
        mApp.registerForConnEvents(mHandler);
    }

    void unregisterForConnEvents() {
        mApp.unregisterForConnEvents(mHandler);
    }
    
    private static final int SWIPE_MIN_DISTANCE = 250;
   // private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_THRESHOLD_VELOCITY = 300;
    private GestureDetector gestureDetector;
    View.OnTouchListener gestureListener;

    class MyGestureDetector extends SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            try {
              //  if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
                //    return false;
                // right to left swipe
                if(e2.getY() - e1.getY() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                 //   Toast.makeText(SelectFilterActivity.this, "Left Swipe", Toast.LENGTH_SHORT).show();
                    
                   closeChatSession();
                   
                   mActivity.refreshChatViews();
                   
                   
                }  
            } catch (Exception e) {
                // nothing
            }
            return false;
        }

    }

    @Override
    protected void onFinishInflate() {
      //  mStatusIcon = (ImageView) findViewById(R.id.statusIcon);
        mDeliveryIcon = (ImageView) findViewById(R.id.deliveryIcon);
       // mTitle = (TextView) findViewById(R.id.title);
        mHistory = (ListView) findViewById(R.id.history);
        mComposeMessage = (EditText) findViewById(R.id.composeMessage);
        mSendButton = (ImageButton) findViewById(R.id.btnSend);
        mHistory.setOnItemClickListener(mOnItemClickListener);

        mStatusWarningView = findViewById(R.id.warning);
        mWarningIcon = (ImageView) findViewById(R.id.warningIcon);
        mWarningText = (TextView) findViewById(R.id.warningText);
        // Gesture detection
        gestureDetector = new GestureDetector(getContext(), new MyGestureDetector());
        gestureListener = new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        };

      
        mOtrSwitch = (CompoundButton)findViewById(R.id.otrSwitch);
        
        mHistory.setOnTouchListener(gestureListener);
        
        mHistory.setOnItemLongClickListener(new OnItemLongClickListener ()
        {

            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                
                
             if (arg1 instanceof MessageView)
             {

                 // Gets a handle to the clipboard service.
                 ClipboardManager clipboard = (ClipboardManager)
                         mActivity.getSystemService(Context.CLIPBOARD_SERVICE);

                 
                 String textToCopy = ((MessageView)arg1).getLastMessage();
                 
                 ClipData clip = ClipData.newPlainText("chat",textToCopy);
    
                 clipboard.setPrimaryClip(clip);
                 
                 Toast.makeText(mActivity, "message copied to the clipboard", Toast.LENGTH_SHORT).show();
                 
                 return true;
                 
             }
                
                return false;
            }
            
        });
        
        mWarningText.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
               viewProfile();
                
            }

           

        });
        
        mOtrSwitch.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View view) {
                mActivity.setOTRState(ChatView.this, ChatView.this.getOtrChatSession(), mOtrSwitch.isChecked());
            }
        });
       
        /*
        mWarningText.setOnLongClickListener(new OnLongClickListener()
        {

            @Override
            public boolean onLongClick(View v) {
                
                mActivity.switchOtrState(ChatView.this);
                
                return true;
            }
            
        });*/

        mComposeMessage.setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                        sendMessage();
                        return true;

                    case KeyEvent.KEYCODE_ENTER:
                        if (event.isAltPressed()) {
                            mComposeMessage.append("\n");
                            return true;
                        }
                    }
                }
                return false;
            }
        });

        mComposeMessage.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event != null) {
                    if (event.isAltPressed()) {
                        return false;
                    }
                }

                InputMethodManager imm = (InputMethodManager)mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null && imm.isActive(v)) {
                    imm.hideSoftInputFromWindow(getWindowToken(), 0);
                }
                sendMessage();
                return true;
            }
        });

        // TODO: this is a hack to implement BUG #1611278, when dispatchKeyEvent() works with
        // the soft keyboard, we should remove this hack.
        mComposeMessage.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int before, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int after) {
                //log("TextWatcher: " + s);
                userActionDetected();
            }

            public void afterTextChanged(Editable s) {
            }
        });

        mSendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                sendMessage();
            }
        });
        
        initEmoji();
        
        
        
    }

    private static EmojiManager emojiManager = null;
    
    private synchronized void initEmoji ()
    {
        if (emojiManager == null)
        {
            emojiManager = EmojiManager.getInstance(mContext);

            try
            {
                emojiManager.addJsonDefinitions("emoji/phantom.json", "emoji/phantom", "png");
             
                emojiManager.addJsonPlugins();
                
            }
            catch (JsonSyntaxException jse)
            {
                    Log.e(ImApp.LOG_TAG,"could not parse json", jse);
            }
            catch (IOException fe)
            {
                    Log.e(ImApp.LOG_TAG,"could not load emoji definition",fe);
            }       
            catch (Exception fe)
            {
                    Log.e(ImApp.LOG_TAG,"could not load emoji definition",fe);
            }      
        }
        
        
        mEmojiPager = (ViewPager)this.findViewById(R.id.emojiPager);
            
        Collection<EmojiGroup> emojiGroups = emojiManager.getEmojiGroups();
        
        EmojiPagerAdapter emojiPagerAdapter = new EmojiPagerAdapter(mActivity, mComposeMessage, new ArrayList<EmojiGroup>(emojiGroups));
      
        mEmojiPager.setAdapter(emojiPagerAdapter);
        
        ImageView btnEmoji = (ImageView)findViewById(R.id.btnEmoji);
        btnEmoji.setOnClickListener(new OnClickListener ()
        {

            @Override
            public void onClick(View v) {
                 
                if (mEmojiPager.getVisibility() == View.GONE)
                    mEmojiPager.setVisibility(View.VISIBLE);
                else
                    mEmojiPager.setVisibility(View.GONE);
            }
            
        });

        
           
        
    }
  
    public void startListening() {
        if (mViewType == VIEW_TYPE_CHAT) {
            Cursor cursor = getMessageCursor();
            if (cursor == null) {
                startQuery(getChatId());
            } else {
                requeryCursor();
            }
        }
        registerChatListener();
        registerForConnEvents();

        updateWarningView();
    }

    public void stopListening() {
        Cursor cursor = getMessageCursor();
        if (cursor != null) {
            cursor.deactivate();
        }
        cancelRequery();
        if (mViewType == VIEW_TYPE_CHAT && mCurrentChatSession != null) {
            try {
                mCurrentChatSession.markAsRead();
            } catch (RemoteException e) {
                mHandler.showServiceErrorAlert();
            }
        }
        unregisterChatListener();
        unregisterForConnEvents();
        unregisterChatSessionListener();
    }

    
    
    void updateChat() {
        setViewType(VIEW_TYPE_CHAT);

        updateContactInfo();

        setStatusIcon();
        
        IImConnection conn = mApp.getConnection(mProviderId);
        if (conn == null) {
            if (Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG))
                log("Connection has been signed out");
          
            return;
        }
        
        mMessageAdapter = new MessageAdapter(mActivity, null);
        mHistory.setAdapter(mMessageAdapter);
        mHistory.invalidate();
        
        startQuery(getChatId());
        mComposeMessage.setText("");
        mOtrChatSession = null;
    
        updateWarningView();
        setDeliveryIcon();
    }

    private void updateContactInfo() {
       // mChatId = mCursor.getLong(CONTACT_ID_COLUMN);
        mProviderId = mCursor.getLong(PROVIDER_COLUMN);
        mAccountId = mCursor.getLong(ACCOUNT_COLUMN);
        mPresenceStatus = mCursor.getInt(PRESENCE_STATUS_COLUMN);
        mType = mCursor.getInt(TYPE_COLUMN);
        mUserName = mCursor.getString(USERNAME_COLUMN);
        mNickName = mCursor.getString(NICKNAME_COLUMN);
    }

    /*
    private void setTitle() {
        
        if (mType == Imps.Contacts.TYPE_GROUP) {
            final String[] projection = { Imps.GroupMembers.NICKNAME };
            Uri memberUri = ContentUris.withAppendedId(Imps.GroupMembers.CONTENT_URI, mChatId);
            ContentResolver cr = mActivity.getContentResolver();
            Cursor c = cr.query(memberUri, projection, null, null, null);
            StringBuilder buf = new StringBuilder();
            BrandingResources brandingRes = mApp.getBrandingResource(mProviderId);

            if (c != null) {
                while (c.moveToNext()) {

                    String nickname = c.getString(c.getColumnIndexOrThrow(Imps.Contacts.NICKNAME));
                    int status = c.getInt(c.getColumnIndexOrThrow(Imps.Contacts.PRESENCE_STATUS));
                    buf.append(nickname);
                    buf.append(" (");
                    buf.append(brandingRes.getString(PresenceUtils.getStatusStringRes(this.mPresenceStatus)));
                    buf.append(")");
                    if (!c.isLast()) {
                        buf.append(',');
                    }
                }
              
            }
            
            mActivity.setTitle(buf.toString());
            
        } else {
            
        
            StringBuilder buf = new StringBuilder();
           
            BrandingResources brandingRes = mApp.getBrandingResource(mProviderId);
           
            buf.append(this.mNickName);
            buf.append(" (");
            buf.append(brandingRes.getString(PresenceUtils.getStatusStringRes(this.mPresenceStatus)));
            buf.append(")");
            
            mActivity.setTitle(buf.toString());
       
            Drawable avatar = loadAvatar(mUserName);
            
           // if (avatar != null)
           // mActivity.setHomeIcon(avatar);
            
       // }
    }*/
    
    private Drawable loadAvatar (String jid)
    {
        try
        {
            //String filename = Base64.encodeBase64String(jid.getBytes()) + ".jpg";
            String fileName = Base64.encodeToString(jid.getBytes(), Base64.NO_WRAP) + ".jpg";
            File sdCard = new File(mActivity.getCacheDir(),"avatars");
            File fileAvatar = new File(sdCard, fileName);
            
            if (fileAvatar.exists())
            {
                return new BitmapDrawable(BitmapFactory.decodeFile(fileAvatar.getCanonicalPath()));
            }
            else
                return null;
        }
        catch (IOException ioe)
        {
            Log.e("Contacts","error loading avatar",ioe);
            return null;
        }
    }

    private void setStatusIcon() {
        if (mType == Imps.Contacts.TYPE_GROUP) {
            // hide the status icon for group chat.
         //   mStatusIcon.setVisibility(GONE);
        } else {
          //  mStatusIcon.setVisibility(VISIBLE);
            BrandingResources brandingRes = mApp.getBrandingResource(mProviderId);
            int presenceResId = PresenceUtils.getStatusIconId(mPresenceStatus);
            //mStatusIcon.setImageDrawable(brandingRes.getDrawable(presenceResId));
            
        }
    }

    private void setDeliveryIcon() {
        if (mExpectingDelivery) {
            mDeliveryIcon.setVisibility(VISIBLE);
        } else {
            mDeliveryIcon.setVisibility(GONE);
        }
    }

    public void rebind ()
    {
        bindChat(mLastChatId);
    }
    
    private void deleteChat ()
    {
        Uri chatUri = ContentUris.withAppendedId(Imps.Chats.CONTENT_URI, mLastChatId);
        mActivity.getContentResolver().delete(chatUri,null,null);
    }
    
    public void bindChat(long contactId) {
        
        mLastChatId = contactId;
        
        if (mCursor != null) {
            mCursor.deactivate();
        }
        
        Uri contactUri = ContentUris.withAppendedId(Imps.Contacts.CONTENT_URI, contactId);
        mCursor = mActivity.managedQuery(contactUri, CHAT_PROJECTION, null, null, null);
        
        if (mCursor == null || !mCursor.moveToFirst()) {
            if (Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
                log("Failed to query chat: " + contactId);
            }
            mLastChatId = -1;
        } else {
            
            mCurrentChatSession = getChatSession(mCursor);

            updateChat();
            
            if (mCurrentChatSession != null)
            {
                // This will save the current chatId and providerId in the relevant fields.
                // getChatSessionManager depends on mProviderId getting the cursor value of providerId.
                
                registerChatListener();
            }
        }
        
        updateWarningView();
        
        
    }
    
    private IChatSession getChatSession ()
    {
        return getChatSession(false);
    }
    
    private IChatSession getChatSession (boolean autoInit)
    {
        if (mCurrentChatSession == null && autoInit)
            bindChat(mLastChatId);
        
        return mCurrentChatSession;
    }

    private void initOtr()  {

        try
        {
            //if (mOtrChatSession == null && getChatSession () != null) {
            
            if (getChatSession() != null)
                mOtrChatSession = getChatSession ().getOtrChatSession();
            else
                mOtrChatSession = null;
    
            if (mOtrChatSession != null) {
    
                    mOtrKeyManager = getChatSession ().getOtrKeyManager();
    
                
             }
        }
        catch (Exception e)
        {
            Log.e(ImApp.LOG_TAG,"error setting up OTR session",e);
        }

    }

    public void bindInvitation(long invitationId) {
        Uri uri = ContentUris.withAppendedId(Imps.Invitation.CONTENT_URI, invitationId);
        ContentResolver cr = mActivity.getContentResolver();
        Cursor cursor = cr.query(uri, INVITATION_PROJECT, null, null, null);
        if (cursor == null || !cursor.moveToFirst()) {
            if (Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
                log("Failed to query invitation: " + invitationId);
            }
          //  mActivity.finish();
        } else {
            setViewType(VIEW_TYPE_INVITATION);

            mInvitationId = cursor.getLong(INVITATION_ID_COLUMN);
            mProviderId = cursor.getLong(INVITATION_PROVIDER_COLUMN);
            String sender = cursor.getString(INVITATION_SENDER_COLUMN);

            TextView mInvitationText = (TextView) findViewById(R.id.txtInvitation);
            mInvitationText.setText(mContext.getString(R.string.invitation_prompt, sender));
            mActivity.setTitle(mContext.getString(R.string.chat_with, sender));
        }

       
    }

    public void bindSubscription(long providerId, String from) {
        mProviderId = providerId;
        mUserName = from;

        setViewType(VIEW_TYPE_SUBSCRIPTION);

        TextView text = (TextView) findViewById(R.id.txtSubscription);
        String displayableAddr = ImpsAddressUtils.getDisplayableAddress(from);
        text.setText(mContext.getString(R.string.subscription_prompt, displayableAddr));
        mActivity.setTitle(mContext.getString(R.string.chat_with, displayableAddr));

        mApp.dismissChatNotification(providerId, from);
    }

    

    private void setViewType(int type) {
        mViewType = type;
        if (type == VIEW_TYPE_CHAT) {
            findViewById(R.id.invitationPanel).setVisibility(GONE);
            findViewById(R.id.subscription).setVisibility(GONE);
            setChatViewEnabled(true);
        } else if (type == VIEW_TYPE_INVITATION) {
            setChatViewEnabled(false);
            findViewById(R.id.invitationPanel).setVisibility(VISIBLE);
            findViewById(R.id.btnAccept).requestFocus();
        } else if (type == VIEW_TYPE_SUBSCRIPTION) {
            setChatViewEnabled(false);
            findViewById(R.id.subscription).setVisibility(VISIBLE);
            findViewById(R.id.btnApproveSubscription).requestFocus();
        }
    }

    private void setChatViewEnabled(boolean enabled) {
        mComposeMessage.setEnabled(enabled);
        mSendButton.setEnabled(enabled);
        if (enabled) {
            mComposeMessage.requestFocus();
        } else {
            mHistory.setAdapter(null);
        }

    }

    ListView getHistoryView() {
        return mHistory;
    }

    private void startQuery(long chatId) {
        if (mQueryHandler == null) {
            mQueryHandler = new QueryHandler(mContext);
        } else {
            // Cancel any pending queries
            mQueryHandler.cancelOperation(QUERY_TOKEN);
        }

        Uri uri = Imps.Messages.getContentUriByThreadId(chatId);

        if (Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
            log("queryCursor: uri=" + uri);
        }

        mQueryHandler.startQuery(QUERY_TOKEN, null, uri, null, null /* selection */,
                null /* selection args */, "date");
    }

    void scheduleRequery(long interval) {
        if (mRequeryCallback == null) {
            mRequeryCallback = new RequeryCallback();
        } else {
            mHandler.removeCallbacks(mRequeryCallback);
        }

        if (Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
            log("scheduleRequery");
        }
        mHandler.postDelayed(mRequeryCallback, interval);
    }

    void cancelRequery() {
        if (mRequeryCallback != null) {
            if (Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
                log("cancelRequery");
            }
            mHandler.removeCallbacks(mRequeryCallback);
            mRequeryCallback = null;
        }
    }

    void requeryCursor() {
        if (mMessageAdapter.isScrolling()) {
            mMessageAdapter.setNeedRequeryCursor(true);
            return;
        }

        // This is redundant if there are messages in view, because the cursor requery will update everything.
        // However, if there are no messages, no update will trigger below, and we still want this to update.
        updateWarningView();

        // TODO: async query?
        Cursor cursor = getMessageCursor();
        if (cursor != null) {
            cursor.requery();
        }
    }

    private Cursor getMessageCursor() {
        return mMessageAdapter == null ? null : mMessageAdapter.getCursor();
    }

    public void closeChatSession() {
        if (getChatSession() != null) {
            try {
                getChatSession().leave();
                
            } catch (RemoteException e) {
                mHandler.showServiceErrorAlert();
            }
        } 
        
        deleteChat();
                
    }

    public void closeChatSessionIfInactive() {
        if (getChatSession() != null) {
            try {
                getChatSession().leaveIfInactive();
            } catch (RemoteException e) {
                mHandler.showServiceErrorAlert();
            }
        }
        
        deleteChat();
    
    }

    public void viewProfile() {
        String remoteFingerprint = null;
        String localFingerprint = null;
        boolean isVerified = false;

        if (mOtrKeyManager == null)
            initOtr();

        Uri data = ContentUris.withAppendedId(Imps.Contacts.CONTENT_URI, getChatId());

        Intent intent = new Intent(Intent.ACTION_VIEW, data);
        intent.putExtra(ImServiceConstants.EXTRA_INTENT_PROVIDER_ID, mProviderId);
        intent.putExtra(ImServiceConstants.EXTRA_INTENT_ACCOUNT_ID, mAccountId);

        if (mOtrKeyManager != null) {
            try {

                
                localFingerprint = mOtrKeyManager.getLocalFingerprint();
                
                remoteFingerprint = mOtrKeyManager.getRemoteFingerprint();
                
                if (remoteFingerprint != null)
                    isVerified = mOtrKeyManager.isKeyVerified(mUserName);
                else
                    isVerified = false;
                
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            // TODO define these in ImServiceConstants
            intent.putExtra("remoteFingerprint", remoteFingerprint);
            intent.putExtra("localFingerprint", localFingerprint);

            intent.putExtra("remoteVerified", isVerified);
        }

        mActivity.startActivity(intent);

    }

    public void blockContact() {
        // TODO: unify with codes in ContactListView
        DialogInterface.OnClickListener confirmListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                try {
                    IImConnection conn = mApp.getConnection(mProviderId);
                    IContactListManager manager = conn.getContactListManager();
                    manager.blockContact(mUserName);
                  //  mActivity.finish();
                } catch (RemoteException e) {
                    mHandler.showServiceErrorAlert();
                }
            }
        };

        Resources r = getResources();

        // The positive button is deliberately set as no so that
        // the no is the default value
        new AlertDialog.Builder(mContext).setTitle(R.string.confirm)
                .setMessage(r.getString(R.string.confirm_block_contact, mNickName))
                .setPositiveButton(R.string.yes, confirmListener) // default button
                .setNegativeButton(R.string.no, null).setCancelable(false).show();
    }

    public long getProviderId() {
        return mProviderId;
    }

    public long getAccountId() {
        return mAccountId;
    }

    public String getUserName() {
        return mUserName;
    }

    public long getChatId() {
        try {
            return getChatSession() == null ? -1 : getChatSession().getId();
        } catch (RemoteException e) {
            mHandler.showServiceErrorAlert();
            return -1;
        }
    }

    private IChatSessionManager getChatSessionManager(long providerId) {
        if (mChatSessionManager == null || mProviderId != providerId) {

            IImConnection conn = mApp.getConnection(providerId);

            if (conn != null) {
                try {
                    mChatSessionManager = conn.getChatSessionManager();
                } catch (RemoteException e) {
                    mHandler.showServiceErrorAlert();
                }
            }
        }

        return mChatSessionManager;
    }

    public IOtrKeyManager getOtrKeyManager() {
        initOtr();

        return mOtrKeyManager;
    }

    public IOtrChatSession getOtrChatSession() {
        initOtr();

        return mOtrChatSession;
    }

    private IChatSession getChatSession(Cursor cursor) {
        long providerId = cursor.getLong(PROVIDER_COLUMN);
        String username = cursor.getString(USERNAME_COLUMN);

        IChatSessionManager sessionMgr = getChatSessionManager(providerId);
        if (sessionMgr != null) {
            try {
                return sessionMgr.getChatSession(username);
            } catch (RemoteException e) {
                mHandler.showServiceErrorAlert();
            }
        }

        return null;
    }

    boolean isGroupChat() {
        
        boolean isGroupChat = false;
        
        if (mCurrentChatSession != null)
        {
            try {
                isGroupChat = mCurrentChatSession.isGroupChatSession();
            }
            catch (Exception e){}
            
        }
           
        return isGroupChat;
    }

    void sendMessage() {
        String msg = mComposeMessage.getText().toString();

        if (TextUtils.isEmpty(msg.trim())) {
            return;
        }
        
        IChatSession session = getChatSession(true);
        
        if (session != null) {
            try {
                session.sendMessage(msg);
                mComposeMessage.setText("");
                mComposeMessage.requestFocus();
                requeryCursor();
            } catch (RemoteException e) {
                mHandler.showServiceErrorAlert();
            } catch (Exception e) {
                mHandler.showServiceErrorAlert();
            }
        }
    }
    
    void sendMessage(String msg) {

        if (TextUtils.isEmpty(msg.trim())) {
            return;
        }

        if (getChatSession() != null) {
            try {
                getChatSession().sendMessage(msg);
                requeryCursor();
            } catch (RemoteException e) {
                mHandler.showServiceErrorAlert();
            } catch (Exception e) {
                mHandler.showServiceErrorAlert();
            }
        }
    }

    void registerChatListener() {
        if (Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
            log("registerChatListener");
        }
        try {
            if (getChatSession() != null) {
                getChatSession().registerChatListener(mChatListener);
            }
            IImConnection conn = mApp.getConnection(mProviderId);
            if (conn != null) {
                IContactListManager listMgr = conn.getContactListManager();
                listMgr.registerContactListListener(mContactListListener);
            }
            mApp.dismissChatNotification(mProviderId, mUserName);
        } catch (RemoteException e) {
            Log.w(ImApp.LOG_TAG, "<ChatView> registerChatListener fail:" + e.getMessage());
        }
    }

    void unregisterChatListener() {
        if (Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
            log("unregisterChatListener");
        }
        try {
            if (getChatSession() != null) {
                getChatSession().unregisterChatListener(mChatListener);
            }
            IImConnection conn = mApp.getConnection(mProviderId);
            if (conn != null) {
                IContactListManager listMgr = conn.getContactListManager();
                listMgr.unregisterContactListListener(mContactListListener);
            }
        } catch (RemoteException e) {
            Log.w(ImApp.LOG_TAG, "<ChatView> unregisterChatListener fail:" + e.getMessage());
        }
    }

    void registerChatSessionListener() {
        IChatSessionManager sessionMgr = getChatSessionManager(mProviderId);
        if (sessionMgr != null) {
            mChatSessionListener = new ChatSessionListener();
            try {
                sessionMgr.registerChatSessionListener(mChatSessionListener);
            } catch (RemoteException e) {
                mHandler.showServiceErrorAlert();
            }
        }
    }

    void unregisterChatSessionListener() {
        if (mChatSessionListener != null) {
            try {
                IChatSessionManager sessionMgr = getChatSessionManager(mProviderId);
                sessionMgr.unregisterChatSessionListener(mChatSessionListener);
                // We unregister the listener when the chat session we are
                // waiting for has been created or the activity is stopped.
                // Clear the listener so that we won't unregister the listener
                // twice.
                mChatSessionListener = null;
            } catch (RemoteException e) {
                mHandler.showServiceErrorAlert();
            }
        }
    }

    void updateWarningView() {
        int visibility = View.GONE;
        int iconVisibility = View.GONE;
        String message = null;
        boolean isConnected;

        SessionStatus sessionStatus = null;

        if (this.isGroupChat())
        {
            //no OTR in group chat
            mStatusWarningView.setVisibility(View.GONE);
            return;
        }
        
        initOtr();

        //check if the chat is otr or not
        if (mOtrChatSession != null) {
            try {
                sessionStatus = SessionStatus.values()[mOtrChatSession.getChatStatus()];
            } catch (RemoteException e) {
                Log.w("Gibber", "Unable to call remote OtrChatSession from ChatView", e);
            }
        }

        try {
            IImConnection conn = mApp.getConnection(mProviderId);
            isConnected = (conn == null) ? false : conn.getState() != ImConnection.SUSPENDED;
        } catch (RemoteException e) {
           
            isConnected = false;
        }

        if (isConnected) {

            if (mType == Imps.Contacts.TYPE_GROUP) {
                visibility = View.GONE;
                message = "";
            }
            else if (mType == Imps.Contacts.TYPE_TEMPORARY) {
                visibility = View.VISIBLE;
                message = mContext.getString(R.string.contact_not_in_list_warning, mNickName);
            } else if (mPresenceStatus == Imps.Presence.OFFLINE) {
                visibility = View.VISIBLE;
                message = mContext.getString(R.string.contact_offline_warning, mNickName);
            } else {

                visibility = View.VISIBLE;

            }

            if (mPresenceStatus == Imps.Presence.OFFLINE)
            {
                mWarningText.setTextColor(Color.WHITE);
                mStatusWarningView.setBackgroundColor(Color.DKGRAY);
                message = mContext.getString(R.string.presence_offline);
                mOtrSwitch.setChecked(false);
                
            }
            else if (sessionStatus == SessionStatus.ENCRYPTED) {
                try {

                    if (mOtrKeyManager == null)
                        initOtr();

                    mOtrSwitch.setChecked(true);
                    
                    String rFingerprint = mOtrKeyManager.getRemoteFingerprint();
                    boolean rVerified = mOtrKeyManager.isKeyVerified(mUserName);

                    if (rFingerprint != null) {
                        if (!rVerified) {
                            message = mContext.getString(R.string.otr_session_status_encrypted);

                            mWarningText.setTextColor(Color.BLACK);
                            mStatusWarningView.setBackgroundResource(R.color.otr_yellow);
                        } else {
                            message = mContext.getString(R.string.otr_session_status_verified);

                            mWarningText.setTextColor(Color.BLACK);
                            mStatusWarningView.setBackgroundResource(R.color.otr_green);
                        }
                    } else {
                        mWarningText.setTextColor(Color.WHITE);
                        mStatusWarningView.setBackgroundResource(R.color.otr_red);
                        message = mContext.getString(R.string.otr_session_status_plaintext);
                    }

                  //  ImageView imgSec = (ImageView) findViewById(R.id.composeSecureIcon);
//                    imgSec.setImageResource(R.drawable.ic_menu_encrypt);

                    
                 //   mSendButton.setCompoundDrawablesWithIntrinsicBounds( getContext().getResources().getDrawable(R.drawable.ic_menu_encrypt ), null, null, null );
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } else if (sessionStatus == SessionStatus.FINISHED) {
            //    mSendButton.setCompoundDrawablesWithIntrinsicBounds( getContext().getResources().getDrawable(R.drawable.ic_menu_unencrypt ), null, null, null );
                mOtrSwitch.setChecked(true);
                
                mWarningText.setTextColor(Color.WHITE);
                mStatusWarningView.setBackgroundColor(Color.DKGRAY);
                message = mContext.getString(R.string.otr_session_status_finished);
            }  
            else if (sessionStatus == SessionStatus.PLAINTEXT) {

            //    mOtrSwitch.setChecked(false);
                
//                ImageView imgSec = (ImageView) findViewById(R.id.composeSecureIcon);
  //              imgSec.setImageResource(R.drawable.ic_menu_unencrypt);

            //    mSendButton.setCompoundDrawablesWithIntrinsicBounds( getContext().getResources().getDrawable(R.drawable.ic_menu_unencrypt ), null, null, null );
                
                mWarningText.setTextColor(Color.WHITE);
                mStatusWarningView.setBackgroundResource(R.color.otr_red);
                message = mContext.getString(R.string.otr_session_status_plaintext);
            }

        } else {
            
            mOtrSwitch.setChecked(false);
            
            
            visibility = View.VISIBLE;
            iconVisibility = View.VISIBLE;
            mWarningText.setTextColor(Color.WHITE);
            mWarningText.setBackgroundColor(Color.DKGRAY);
            message = mContext.getString(R.string.disconnected_warning);
            
        }
        
        mStatusWarningView.setVisibility(visibility);
        if (visibility == View.VISIBLE) {
            mWarningIcon.setVisibility(iconVisibility);
            mWarningText.setText(message);
        }
        

    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        userActionDetected();
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        userActionDetected();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent ev) {
        userActionDetected();
        return super.dispatchTrackballEvent(ev);
    }

    private void userActionDetected() {
        if (getChatSession() != null) {
            try {
                getChatSession().markAsRead();
                // TODO OTRCHAT updateSecureWarning
                //updateSecureWarning();
                updateWarningView();

            } catch (RemoteException e) {
                mHandler.showServiceErrorAlert();
            }
        }
    }

    private final class ChatViewHandler extends SimpleAlertHandler {
      

        public ChatViewHandler(Activity activity) {
            super(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            long providerId = ((long) msg.arg1 << 32) | msg.arg2;
            if (providerId != mProviderId) {
                return;
            }

            switch (msg.what) {

            case ImApp.EVENT_CONNECTION_DISCONNECTED:
                log("Handle event connection disconnected.");
                updateWarningView();
                promptDisconnectedEvent(msg);
                return;
             default:
                 updateWarningView();
            }

            super.handleMessage(msg);
        }
    }

    class ChatSessionListener extends ChatSessionListenerAdapter {
        @Override
        public void onChatSessionCreated(IChatSession session) {
            try {

                if (session.isGroupChatSession()) {
                    final long id = session.getId();
                    unregisterChatSessionListener();
                    mHandler.post(new Runnable() {
                        public void run() {
                            bindChat(id);
                        }
                    });
                }

                updateWarningView();

            } catch (RemoteException e) {
                mHandler.showServiceErrorAlert();
            }
        }
    }

    public static class DeltaCursor implements Cursor {
        static final String DELTA_COLUMN_NAME = "delta";

        private Cursor mInnerCursor;
        private String[] mColumnNames;
        private int mDateColumn = -1;
        private int mDeltaColumn = -1;

        DeltaCursor(Cursor cursor) {
            mInnerCursor = cursor;

            String[] columnNames = cursor.getColumnNames();
            int len = columnNames.length;

            mColumnNames = new String[len + 1];

            for (int i = 0; i < len; i++) {
                mColumnNames[i] = columnNames[i];
                if (mColumnNames[i].equals(Imps.Messages.DATE)) {
                    mDateColumn = i;
                }
            }

            mDeltaColumn = len;
            mColumnNames[mDeltaColumn] = DELTA_COLUMN_NAME;

            //if (DBG) log("##### DeltaCursor constructor: mDeltaColumn=" +
            //        mDeltaColumn + ", columnName=" + mColumnNames[mDeltaColumn]);
        }

        public int getCount() {
            return mInnerCursor.getCount();
        }

        public int getPosition() {
            return mInnerCursor.getPosition();
        }

        public boolean move(int offset) {
            return mInnerCursor.move(offset);
        }

        public boolean moveToPosition(int position) {
            return mInnerCursor.moveToPosition(position);
        }

        public boolean moveToFirst() {
            return mInnerCursor.moveToFirst();
        }

        public boolean moveToLast() {
            return mInnerCursor.moveToLast();
        }

        public boolean moveToNext() {
            return mInnerCursor.moveToNext();
        }

        public boolean moveToPrevious() {
            return mInnerCursor.moveToPrevious();
        }

        public boolean isFirst() {
            return mInnerCursor.isFirst();
        }

        public boolean isLast() {
            return mInnerCursor.isLast();
        }

        public boolean isBeforeFirst() {
            return mInnerCursor.isBeforeFirst();
        }

        public boolean isAfterLast() {
            return mInnerCursor.isAfterLast();
        }

        public int getColumnIndex(String columnName) {
            if (DELTA_COLUMN_NAME.equals(columnName)) {
                return mDeltaColumn;
            }

            int columnIndex = mInnerCursor.getColumnIndex(columnName);
            return columnIndex;
        }

        public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
            if (DELTA_COLUMN_NAME.equals(columnName)) {
                return mDeltaColumn;
            }

            return mInnerCursor.getColumnIndexOrThrow(columnName);
        }

        public String getColumnName(int columnIndex) {
            if (columnIndex == mDeltaColumn) {
                return DELTA_COLUMN_NAME;
            }

            return mInnerCursor.getColumnName(columnIndex);
        }

        public int getColumnCount() {
            return mInnerCursor.getColumnCount() + 1;
        }

        public void deactivate() {
            mInnerCursor.deactivate();
        }

        public boolean requery() {
            return mInnerCursor.requery();
        }

        public void close() {
            mInnerCursor.close();
        }

        public boolean isClosed() {
            return mInnerCursor.isClosed();
        }

        public void registerContentObserver(ContentObserver observer) {
            mInnerCursor.registerContentObserver(observer);
        }

        public void unregisterContentObserver(ContentObserver observer) {
            mInnerCursor.unregisterContentObserver(observer);
        }

        public void registerDataSetObserver(DataSetObserver observer) {
            mInnerCursor.registerDataSetObserver(observer);
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
            mInnerCursor.unregisterDataSetObserver(observer);
        }

        public void setNotificationUri(ContentResolver cr, Uri uri) {
            mInnerCursor.setNotificationUri(cr, uri);
        }

        public boolean getWantsAllOnMoveCalls() {
            return mInnerCursor.getWantsAllOnMoveCalls();
        }

        public Bundle getExtras() {
            return mInnerCursor.getExtras();
        }

        public Bundle respond(Bundle extras) {
            return mInnerCursor.respond(extras);
        }

        public String[] getColumnNames() {
            return mColumnNames;
        }

        private void checkPosition() {
            int pos = mInnerCursor.getPosition();
            int count = mInnerCursor.getCount();

            if (-1 == pos || count == pos) {
                throw new CursorIndexOutOfBoundsException(pos, count);
            }
        }

        public byte[] getBlob(int column) {
            checkPosition();

            if (column == mDeltaColumn) {
                return null;
            }

            return mInnerCursor.getBlob(column);
        }

        public String getString(int column) {
            checkPosition();

            if (column == mDeltaColumn) {
                long value = getDeltaValue();
                return Long.toString(value);
            }

            return mInnerCursor.getString(column);
        }

        public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
            checkPosition();

            if (columnIndex == mDeltaColumn) {
                long value = getDeltaValue();
                String strValue = Long.toString(value);
                int len = strValue.length();
                char[] data = buffer.data;
                if (data == null || data.length < len) {
                    buffer.data = strValue.toCharArray();
                } else {
                    strValue.getChars(0, len, data, 0);
                }
                buffer.sizeCopied = strValue.length();
            } else {
                mInnerCursor.copyStringToBuffer(columnIndex, buffer);
            }
        }

        public short getShort(int column) {
            checkPosition();

            if (column == mDeltaColumn) {
                return (short) getDeltaValue();
            }

            return mInnerCursor.getShort(column);
        }

        public int getInt(int column) {
            checkPosition();

            if (column == mDeltaColumn) {
                return (int) getDeltaValue();
            }

            return mInnerCursor.getInt(column);
        }

        public long getLong(int column) {
            //if (DBG) log("DeltaCursor.getLong: column=" + column + ", mDeltaColumn=" + mDeltaColumn);
            checkPosition();

            if (column == mDeltaColumn) {
                return getDeltaValue();
            }

            return mInnerCursor.getLong(column);
        }

        public float getFloat(int column) {
            checkPosition();

            if (column == mDeltaColumn) {
                return getDeltaValue();
            }

            return mInnerCursor.getFloat(column);
        }

        public double getDouble(int column) {
            checkPosition();

            if (column == mDeltaColumn) {
                return getDeltaValue();
            }

            return mInnerCursor.getDouble(column);
        }

        public boolean isNull(int column) {
            checkPosition();

            if (column == mDeltaColumn) {
                return false;
            }

            return mInnerCursor.isNull(column);
        }

        private long getDeltaValue() {
            int pos = mInnerCursor.getPosition();
            //Log.i(LOG_TAG, "getDeltaValue: mPos=" + mPos);

            long t2, t1;

            if (pos == getCount() - 1) {
                t1 = mInnerCursor.getLong(mDateColumn);
                t2 = System.currentTimeMillis();
            } else {
                mInnerCursor.moveToPosition(pos + 1);
                t2 = mInnerCursor.getLong(mDateColumn);
                mInnerCursor.moveToPosition(pos);
                t1 = mInnerCursor.getLong(mDateColumn);
            }

            return t2 - t1;
        }

        public int getType(int arg0) {
            // TODO Auto-generated method stub
            return 0;
        }
    }

    private class MessageAdapter extends CursorAdapter implements AbsListView.OnScrollListener {
        private int mScrollState;
        private boolean mNeedRequeryCursor;

        private int mNicknameColumn;
        private int mBodyColumn;
        private int mDateColumn;
        private int mTypeColumn;
        private int mErrCodeColumn;
        private int mDeltaColumn;
        private int mDeliveredColumn;

        private LayoutInflater mInflater;

        public MessageAdapter(Activity context, Cursor c) {
            super(context, c, false);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
           
            if (c != null) {
                resolveColumnIndex(c);
            }
        }

        private void resolveColumnIndex(Cursor c) {
            
            
            mNicknameColumn = c.getColumnIndexOrThrow(Imps.Messages.NICKNAME);
            
            mBodyColumn = c.getColumnIndexOrThrow(Imps.Messages.BODY);
            mDateColumn = c.getColumnIndexOrThrow(Imps.Messages.DATE);
            mTypeColumn = c.getColumnIndexOrThrow(Imps.Messages.TYPE);
            mErrCodeColumn = c.getColumnIndexOrThrow(Imps.Messages.ERROR_CODE);
            mDeltaColumn = c.getColumnIndexOrThrow(DeltaCursor.DELTA_COLUMN_NAME);
            mDeliveredColumn = c.getColumnIndexOrThrow(Imps.Messages.IS_DELIVERED);
        }

        @Override
        public void changeCursor(Cursor cursor) {
            super.changeCursor(cursor);
            if (cursor != null) {
                resolveColumnIndex(cursor);
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return mInflater.inflate(R.layout.new_message_item, parent, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            MessageView messageView = (MessageView) view;

            mType = cursor.getInt(mTypeColumn);
            String address = mUserName;
            String nickname = isGroupChat() ? cursor.getString(mNicknameColumn) : mNickName;
            String body = cursor.getString(mBodyColumn);
            long delta = cursor.getLong(mDeltaColumn);
            boolean showTimeStamp = (delta > SHOW_TIME_STAMP_INTERVAL);
            long timestamp = cursor.getLong(mDateColumn);
            
            Date date = showTimeStamp ? new Date(timestamp) : null;
            boolean isDelivered = cursor.getLong(mDeliveredColumn) > 0;
            boolean showDelivery = ((System.currentTimeMillis() - timestamp) > SHOW_DELIVERY_INTERVAL);
            
            MessageView.DeliveryState deliveryState = DeliveryState.NEUTRAL;
            if (showDelivery && !isDelivered && mExpectingDelivery) {
                deliveryState = DeliveryState.UNDELIVERED;
            }
            
            MessageView.EncryptionState encState = EncryptionState.NONE;
            
            switch (mType) {
            case Imps.MessageType.INCOMING:
                if (body != null)
                {
                   messageView.bindIncomingMessage(address, nickname, body, date, mMarkup, isScrolling(), encState, isGroupChat());
                }

                break;

            case Imps.MessageType.OUTGOING:
            case Imps.MessageType.POSTPONED:
                
                if (!isGroupChat())
                {
                    int errCode = cursor.getInt(mErrCodeColumn);
                    if (errCode != 0) {
                        messageView.bindErrorMessage(errCode);
                    } else {
                        messageView.bindOutgoingMessage(null, body, date, mMarkup, isScrolling(),
                                deliveryState, encState);
                    }
                }
                break;

            default:
                messageView.bindPresenceMessage(address, mType, isGroupChat(), isScrolling());
            }


            updateWarningView();

            if (!mExpectingDelivery && isDelivered) {
                log("Setting delivery icon");
                mExpectingDelivery = true;
                setDeliveryIcon();
                scheduleRequery(DEFAULT_QUERY_INTERVAL); // FIXME workaround to no refresh
            } else if (cursor.getPosition() == cursor.getCount() - 1) {
                // if showTimeStamp is false for the latest message, then set a timer to query the
                // cursor again in a minute, so we can update the last message timestamp if no new
                // message is received
                if (Log.isLoggable(ImApp.LOG_TAG, Log.DEBUG)) {
                    log("delta = " + delta + ", showTs=" + showTimeStamp);
                }
                if (!showDelivery) {
                    scheduleRequery(SHOW_DELIVERY_INTERVAL);
                } else if (!showTimeStamp) {
                    scheduleRequery(SHOW_TIME_STAMP_INTERVAL);
                } else {
                    cancelRequery();
                }
            }
        }
        
        

        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                int totalItemCount) {
            // do nothing
        }

        public void onScrollStateChanged(AbsListView view, int scrollState) {
            int oldState = mScrollState;
            mScrollState = scrollState;

            if (getChatSession() != null) {
                try {
                    getChatSession().markAsRead();
                } catch (RemoteException e) {
                    mHandler.showServiceErrorAlert();
                }
            }

            if (oldState == OnScrollListener.SCROLL_STATE_FLING) {
                if (mNeedRequeryCursor) {
                    requeryCursor();
                } else {
                    notifyDataSetChanged();
                }
            }
        }

        boolean isScrolling() {
            return mScrollState == OnScrollListener.SCROLL_STATE_FLING;
        }

        void setNeedRequeryCursor(boolean requeryCursor) {
            mNeedRequeryCursor = requeryCursor;
        }
    }

    Cursor getMessageAtPosition(int position) {
        Object item = mMessageAdapter.getItem(position);
        return (Cursor) item;
    }

    EditText getComposedMessage() {
        return mComposeMessage;
    }
}
