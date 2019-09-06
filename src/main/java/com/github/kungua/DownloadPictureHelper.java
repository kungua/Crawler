package com.github.kungua;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class DownloadPictureHelper {
    public static String wrapLinkWithProtocolIfLinkNotHas(String href) {
        if (href.startsWith("//")) {
            href = "https:" + href;
        }
        return href;
    }

    public static void downloadPicture(String url, String title, int index) {
        String fileType = url.substring(url.lastIndexOf(".") + 1);
        File projectDir = new File(System.getProperty("basedir", System.getProperty("user.dir")));
        try {
            FileUtils.copyURLToFile(
                    new URL(url),
                    new File(projectDir, "images/" + title + index + "." + fileType),
                    10000,
                    10000);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
