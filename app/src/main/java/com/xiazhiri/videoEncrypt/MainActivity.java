package com.xiazhiri.videoEncrypt;

import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.MediaController;
import android.widget.VideoView;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import fi.iki.elonen.NanoHTTPD;

public class MainActivity extends AppCompatActivity {

    @Bind(R.id.videoView)
    VideoView videoView;

    private HTTPServer encryptServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        videoView.setMediaController(new MediaController(this));
    }

    private void playVideo(Uri uri) {
        videoView.setVideoURI(uri);
        videoView.requestFocus();
        videoView.start();
    }

    @OnClick(R.id.playUnencrypted)
    public void playUnencrypted() {
        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.normal);
        playVideo(uri);
    }

    @OnClick(R.id.playEncryptedDirect)
    public void playEncryptedDirect() {
        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.encrypted);
        playVideo(uri);
    }

    @OnClick(R.id.playEncrypted)
    public void playEncrypted() {
        try {
            if (encryptServer == null) {
                encryptServer = new HTTPServer(8080);
            }
            Uri url = Uri.parse("http://localhost" + ":" + encryptServer.getListeningPort());
            playVideo(url);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public class HTTPServer extends NanoHTTPD {

        public HTTPServer(int port) throws IOException {
            super(port);
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        }

        @Override
        public Response serve(IHTTPSession session) {
            Response response = null;
            try {

                InputStream inputStream = getResources().openRawResource(R.raw.encrypted);
                inputStream = new InputStreamEncrypted(inputStream);

                int totalLength = inputStream.available();

                String requestRange = session.getHeaders().get("range");
                if (requestRange == null) {
                    //http 200
                    response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "video/mp4", inputStream, totalLength);
                } else {
                    //http 206

                    //region get RangeStart from head
                    Matcher matcher = Pattern.compile("bytes=(\\d+)-(\\d*)").matcher(requestRange);
                    matcher.find();
                    long start = 0;
                    try { start = Long.parseLong(matcher.group(1)); } catch (Exception e) { e.printStackTrace(); }
                    //endregion

                    inputStream.skip(start);

                    long restLength = totalLength - start;
                    response = NanoHTTPD.newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, "video/mp4", inputStream, restLength);

                    String contentRange = String.format("bytes %d-%d/%d", start, totalLength, totalLength);
                    response.addHeader("Content-Range", contentRange);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return response;
        }

    }

    public class InputStreamEncrypted extends InputStream {

        InputStream encrypted;

        public InputStreamEncrypted(InputStream encrypted) {
            try {
                byte[] b = new byte[11];
                encrypted.read(b);
                if (!"HelloLikaci".equals(new String(b, "UTF-8"))) {
                    encrypted.reset();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.encrypted = encrypted;
        }

        @Override
        public int read() throws IOException {
            return encrypted.read();
        }

        @Override
        public int available() throws IOException {
            return encrypted.available();
        }
    }

}
