import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.*;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoDatabase;
import lombok.SneakyThrows;
import org.bson.Document;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class Main {
    private static String sessionID = null;
    private static final String devID = getCred("devID");
    private static final String authKey = getCred("authKey");

    private static final DateTimeFormatter smiteDateFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
        MongoClient mongoClient = new MongoClient(new MongoClientURI("mongodb://localhost:27017"));
        MongoDatabase database = mongoClient.getDatabase("local");

        sessionID = createSession();
        while(true) {
            String methodName = null;
            ArrayList<String> info = new ArrayList<>();
            String input;

            while (!(input = scanner.nextLine()).isBlank()) {
                if (methodName == null) {
                    methodName = input;
                } else {
                    info.add(input);
                }
            }
            processResult(database, methodName, sendRequest(methodName, info));
        }
    }

    private static void processResult(MongoDatabase db, String methodName, InputStream is) throws IOException {
        switch(methodName) {
            case "getgods" -> {
                String orig = new String(is.readAllBytes());
                JsonArray arr = new Gson().fromJson(orig, JsonArray.class);
                for(JsonElement ele : arr) {
                    System.out.println(ele.toString());
                    db.getCollection("gods").insertOne(Document.parse(ele.toString()));
                }
            }
            default -> {System.out.println(new String(is.readAllBytes()));}
        }

    }

    private static String createSession() throws IOException, NoSuchAlgorithmException {
        return (String) new ObjectMapper()
                            .readValue(sendRequest("createsession", new ArrayList<>()), HashMap.class)
                            .get("session_id");
    }

    private static InputStream sendRequest(String methodName, ArrayList<String> info) throws IOException, NoSuchAlgorithmException {
        StringBuilder URLtoReq = new StringBuilder("https://api.smitegame.com/smiteapi.svc");

        String curTime = LocalDateTime.now(ZoneOffset.UTC).format(smiteDateFormat);
        System.out.println(curTime);
        URLtoReq.append('/').append(methodName).append("json");
        URLtoReq.append('/').append(devID);
        URLtoReq.append('/').append(getSignature(curTime, methodName));
        if(sessionID != null)
            URLtoReq.append('/').append(sessionID);
        URLtoReq.append('/').append(curTime);
        for(String s : info) {
            URLtoReq.append('/').append(s);
        }
        System.out.println("Connecting to "+URLtoReq.toString());

        URL url = new URL(URLtoReq.toString());

        HttpsURLConnection https = (HttpsURLConnection)url.openConnection();

        System.out.println();
        System.out.println(https.getResponseCode() + " " + https.getResponseMessage());
        boolean success = https.getResponseCode() >= 200 && https.getResponseCode() < 300;
        InputStream is = success ? https.getInputStream() : https.getErrorStream();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        is.transferTo(baos);
        InputStream isClone = new ByteArrayInputStream(baos.toByteArray());

        https.disconnect();

        return isClone;
    }

    private static String getSignature(String curTime, String methodName) throws NoSuchAlgorithmException {
        return getMD5Hash(devID + methodName + authKey + curTime);
    }

    private static String getMD5Hash(String toHash) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        StringBuilder toReturn = new StringBuilder();
        for(byte b : md.digest(toHash.getBytes(UTF8))) {
            toReturn.append(String.format("%02X", b));
        }
        return toReturn.toString().toLowerCase();
    }

    @SneakyThrows
    private static String getCred(String credName) {
        return Files.readString(Path.of("./src/main/resources/"+credName));
    }
}
