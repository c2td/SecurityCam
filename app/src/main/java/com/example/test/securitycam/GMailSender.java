package com.example.test.securitycam;

import android.os.AsyncTask;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Security;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class GMailSender extends javax.mail.Authenticator {

    private String mailhost = "smtp.gmail.com";
    private String user;
    private String password;
    private Session session;
    private File mImageFile;

    static {
        Security.addProvider(new JSSEProvider());
    }

    public GMailSender(String user, String password) {
        this.user = user;
        this.password = password;

        Properties props = new Properties();
        props.setProperty("mail.transport.protocol", "smtp");
        props.setProperty("mail.host", mailhost);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.port", "465");
        props.put("mail.smtp.socketFactory.port", "465");
        props.put("mail.smtp.socketFactory.class",
                "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.socketFactory.fallback", "false");
        props.setProperty("mail.smtp.quitwait", "false");

        session = Session.getDefaultInstance(props, this);
    }

    protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(user, password);
    }

    public void sendMailAsync(File imageFile) {
        mImageFile = imageFile;
        new SendEmailAsyncTask().execute();
    }

    class SendEmailAsyncTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... params) {
            try {

                String subject = "Photo from Camera";
                String body = "Photo is here";
                String sender = "test@android.com";
                String recipients = "some_name@somewhere.com";

                MimeMessage message = new MimeMessage(session);
                DataHandler handler = new DataHandler(new ByteArrayDataSource(body.getBytes(), "text/plain"));

                // This mail has 2 part, the BODY and the embedded image
                MimeMultipart multipart = new MimeMultipart("related");
                // first part (the html)
                BodyPart messageBodyPart = new MimeBodyPart();
                String htmlText = "<H1>Photo from Camera</H1><img src=\"cid:image\">";
                messageBodyPart.setContent(htmlText, "text/html");
                // add it
                multipart.addBodyPart(messageBodyPart);

                // second part (the image)
                messageBodyPart = new MimeBodyPart();
                //DataSource fds = new FileDataSource(Uri.parse("file://" + mImageFile.getAbsolutePath()));
                DataSource fds = new FileDataSource(mImageFile);

                messageBodyPart.setDataHandler(new DataHandler(fds));
                messageBodyPart.setHeader("Content-ID", "<image>");

                // add image to the multipart
                multipart.addBodyPart(messageBodyPart);

                // put everything together
                message.setContent(multipart);

                message.setSender(new InternetAddress(sender));
                message.setSubject(subject);
                //message.setDataHandler(handler);
                if (recipients.indexOf(',') > 0) {
                    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipients));
                } else {
                    message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipients));
                }
                Transport.send(message);
            } catch (Exception e) {
                Log.e("--", "Exception occurred: " + e + " " + e.getMessage());
            }
            return true;
        }
    }

    public class ByteArrayDataSource implements DataSource {
        private byte[] data;
        private String type;

        public ByteArrayDataSource(byte[] data, String type) {
            super();
            this.data = data;
            this.type = type;
        }

        public ByteArrayDataSource(byte[] data) {
            super();
            this.data = data;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getContentType() {
            if (type == null)
                return "application/octet-stream";
            else
                return type;
        }

        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(data);
        }

        public String getName() {
            return "ByteArrayDataSource";
        }

        public OutputStream getOutputStream() throws IOException {
            throw new IOException("Not Supported");
        }
    }
}
