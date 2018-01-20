package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Pattern;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String REMOTE_PORT[] = {"11108", "11112", "11116", "11120", "11124"};
    static final int SERVER_PORT = 10000;
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    private static final int TIME_OUT = 2000;
    static int agreed_sequence_no;
    static int proposed_sequence_no;
    static int avd_id;
    private final Uri mUri;

    public GroupMessengerActivity() {
        mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        //final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        final int port =  Integer.parseInt(portStr) * 2;
        Log.e(TAG, "Valuo of port " + port);
        switch (port){
            case 11108:
                    avd_id = 1;
                    break;
            case 11112:
                    avd_id = 2;
                    break;
            case 11116:
                    avd_id = 3;
                    break;
            case 11120:
                    avd_id = 4;
                    break;
            case 11124:
                    avd_id = 5;
                    break;
                default:
                    avd_id = -1;
                    Log.e(TAG, "Avd not supported");
                    break;
        }
        agreed_sequence_no = 0;
        proposed_sequence_no = 0;
        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        final EditText editText = (EditText) findViewById(R.id.editText1);
        final Button button4 = (Button) findViewById(R.id.button4);
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        button4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString();
                editText.setText("");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            ContentValues mContentValues = new ContentValues();

            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            String[] recvdMsg;

            float message_no;
            try {
                while (true) {
                    try {
                        Socket server = serverSocket.accept();
                        Log.e(TAG, "Connection Accepted by server");
                        DataInputStream in = new DataInputStream(server.getInputStream());
                        DataOutputStream outs = new DataOutputStream(server.getOutputStream());
                        //server.setSoTimeout(TIME_OUT);
                        recvdMsg = in.readUTF().split(":#:");
                        Log.e(TAG, "Received Message :" + recvdMsg[0]);
                        message_no = Float.parseFloat(recvdMsg[1]);
                        if (message_no == -1) {
                            proposed_sequence_no = Math.max(proposed_sequence_no, agreed_sequence_no) + 1;
                            String proposed_seq_avd = proposed_sequence_no + "." + avd_id;
                            Log.e(TAG, "Got request for propose sequence number. Proposing seq no : " + proposed_seq_avd);
                            outs.writeUTF(proposed_seq_avd);
                            outs.flush();
                            outs.close();
                            in.close();
                            server.close();
                        } else {
                            int message_no_int = (int) message_no;
                            agreed_sequence_no = Math.max(message_no_int, agreed_sequence_no);
                            outs.writeUTF("PA2B OK");
                            outs.flush();
                            Log.e(TAG, "Got agreed sequence number :" + message_no);
                            mContentValues.put(KEY_FIELD, message_no);
                            mContentValues.put(VALUE_FIELD, recvdMsg[0]);
                            getContentResolver().insert(mUri, mContentValues);
                            publishProgress(recvdMsg[0]);
                            outs.close();
                            in.close();
                            server.close();
                            mContentValues.clear();
                            if (recvdMsg.equals("bye")) {
                                break;
                            }
                        }
                    } /* catch (SocketTimeoutException e) {
                        Log.e(TAG, "Servertask socket Timeout Exception");
                    } */catch (StreamCorruptedException e) {
                        Log.e(TAG, "Servertask stream corrupted Exception");
                    } catch (EOFException e) {
                        Log.e(TAG, "Servertask eof exception 2");
                    } catch (FileNotFoundException e) {
                        Log.e(TAG, "Servertask file not found exception");
                    } catch (IOException e) {
                        Log.e(TAG, "Servertask socket IOException");
                    }
                }
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Can't create a listening socket for server");
            }
            return null;
        }

        protected void onProgressUpdate(String... strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            TextView tv = (TextView) findViewById(R.id.textView1);
            tv.append(strReceived + "\n");

            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             *
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html
             */
            return;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            Socket[] socket = new Socket[5];
            DataInputStream[] inc = new DataInputStream[5];
            DataOutputStream[] out = new DataOutputStream[5];
            boolean socket_failed[] = new boolean[5];
            Arrays.fill(socket_failed, false);
            int request_indicator = -1;
            float largest_recvd_sequence_no = 0;

            String msgToSend1 = msgs[0] + ":#:" + request_indicator;
            for(int i = 0; i < 5; i++) {
                try {
                    socket[i] = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(REMOTE_PORT[i]));
                    out[i] = new DataOutputStream(socket[i].getOutputStream());
                    inc[i] = new DataInputStream(socket[i].getInputStream());
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                    socket_failed[i] = true;
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException");
                    socket_failed[i] = true;
                }
            }

            for (int i = 0; i < 5; i++) {
                if(!socket_failed[i]) {
                    try {
                        //socket[i].setSoTimeout(TIME_OUT);
                        out[i].writeUTF(msgToSend1);
                        out[i].flush();
                        Log.e(TAG, "Message sent : " + msgToSend1);
                        float recvd_sequence_no = Float.parseFloat(inc[i].readUTF());
                        if (recvd_sequence_no > largest_recvd_sequence_no) {
                            largest_recvd_sequence_no = recvd_sequence_no;
                        }
                        //while (!inc[i].readUTF().equals("PA-2 OK"));
                        inc[i].close();
                        out[i].close();
                        socket[i].close();
                    } /*catch (SocketTimeoutException e) {
                        socket_failed[i] = true;
                        Log.e(TAG, "ClientTask socket Timeout Exception 2");
                    }*/ catch (StreamCorruptedException e) {
                        socket_failed[i] = true;
                        Log.e(TAG, "ClientTask stream corrupted Exception 2");
                    } catch (EOFException e) {
                        socket_failed[i] = true;
                        Log.e(TAG, "ClientTask eof exception 2");
                    } catch (FileNotFoundException e) {
                        socket_failed[i] = true;
                        Log.e(TAG, "ClientTask file not found exception 2");
                    } catch (IOException e) {
                        socket_failed[i] = true;
                        Log.e(TAG, "ClientTask socket IOException 2");
                    }
                }
            }

            for(int i = 0; i < 5; i++) {
                if(!socket_failed[i]) {
                    try {
                        socket[i] = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(REMOTE_PORT[i]));
                        out[i] = new DataOutputStream(socket[i].getOutputStream());
                        inc[i] = new DataInputStream(socket[i].getInputStream());
                    } catch (UnknownHostException e) {
                        socket_failed[i] = true;
                        Log.e(TAG, "ClientTask UnknownHostException");
                    } catch (IOException e) {
                        socket_failed[i] = true;
                        Log.e(TAG, "ClientTask socket IOException");
                    }
                }
            }

            String msgToSend2 = msgs[0] + ":#:" + largest_recvd_sequence_no;

            for (int i = 0; i < 5; i++) {
                if(!socket_failed[i]) {
                    try {
                        //socket[i].setSoTimeout(TIME_OUT);
                        out[i].writeUTF(msgToSend2);
                        out[i].flush();
                        Log.e(TAG, "Message sent 2 : " + msgToSend2);
                        while (!inc[i].readUTF().equals("PA2B OK")) ;
                        inc[i].close();
                        out[i].close();
                        socket[i].close();
                    } /*catch (SocketTimeoutException e) {
                        Log.e(TAG, "ClientTask socket Timeout Exception 3");
                    } */catch (StreamCorruptedException e) {
                        socket_failed[i] = true;
                        Log.e(TAG, "ClientTask stream corrupted Exception 3");
                    } catch (EOFException e) {
                        socket_failed[i] = true;
                        Log.e(TAG, "ClientTask eof exception 3");
                    } catch (FileNotFoundException e) {
                        socket_failed[i] = true;
                        Log.e(TAG, "ClientTask file not found exception 3");
                    } catch (IOException e) {
                        socket_failed[i] = true;
                        Log.e(TAG, "ClientTask socket IOException 3");
                    }
                }
            }

            return null;
        }
    }
}
