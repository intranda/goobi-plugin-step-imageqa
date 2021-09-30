package de.intranda.goobi.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;

import com.google.gson.Gson;

/**
 * Class for saving comments per image file
 * 
 * @author joel
 *
 */
public class ImageCommentHelper {

    private static Gson gson = new Gson();
    //dictionary of comment files, so each is only read once    
    private HashMap<String, ImageComments> commentFiles;

    public ImageCommentHelper() {

        commentFiles = new HashMap<String, ImageComments>();

    }

    private ImageComments getCommentFile(String imageFolderName) {

        if (!commentFiles.containsKey(imageFolderName)) {
            try {

                String strCommentFile = imageFolderName + "imageComments.json";

                File commentsFile = new File(strCommentFile);

                if (commentsFile.exists()) {

                    BufferedReader br;
                    br = new BufferedReader(new FileReader(strCommentFile));
                    ImageComments commentsClassNew = gson.fromJson(br, ImageComments.class);

                    commentFiles.put(imageFolderName, commentsClassNew);

                } else {

                    ImageComments commentsClassNew = new ImageComments();
                    commentFiles.put(imageFolderName, commentsClassNew);
                }

            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return commentFiles.get(imageFolderName);
    }

    public String getComment(String imageFolderName, String imageName) {

        ImageComments commentsClass = getCommentFile(imageFolderName);

        return commentsClass.getComment(imageName);

    }

    public void setComment(String imageFolderName, String imageName, String comment) {

        ImageComments commentsClass = getCommentFile(imageFolderName);
        commentsClass.setComment(imageName, comment);

        try (Writer writer = new FileWriter(imageFolderName + "imageComments.json")) {
            gson.toJson(commentsClass, writer);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public class ImageComments {

        private HashMap<String, String> comments;

        public ImageComments() {
            comments = new HashMap<String, String>();
        }

        public String getComment(String imageName) {

            //            if (comments.containsKey(imageName)) {
            return comments.get(imageName);
            //            }
            //
            //            //otherwise
            //            return null;
        }

        public void setComment(String imageName, String comment) {

            comments.put(imageName, comment);
        }

    }
}
