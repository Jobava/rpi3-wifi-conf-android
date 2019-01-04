package io.brendanmyers.rpiconf;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MessagesActivity extends Activity {
    private TextView messageTextView;

    Thread thread = new Thread() {
        @Override
        public void run() {
            try {
                while (!thread.isInterrupted()) {
                    Thread.sleep(1000);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            messageTextView.setText(Globals.getMessages());
                        }
                    });
                }
            } catch (InterruptedException e) {
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.messages);
        messageTextView = (TextView) findViewById(R.id.messages_text);
        thread.start();
    }
}

