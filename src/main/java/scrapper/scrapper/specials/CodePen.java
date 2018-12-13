package scrapper.scrapper.specials;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;


import java.util.stream.Stream;

import javax.net.ssl.HttpsURLConnection;

import javafx.application.Platform;
import scrapper.EnvConfig;
import scrapper.scrapper.BufferHandler;
import scrapper.scrapper.ScrappingResult;
import scrapper.scrapper.UrlContainer;
@Details(value="codepen", rss="http://codepen.io/picks/feed")
public class CodePen extends Specials {
    private volatile ConcurrentSkipListSet<String> existingPens;
    private static volatile boolean dirCreated;

    /**
     * login required to download thus removed
     * @param c
     * @return
     */
    protected Callable<ScrappingResult> toTask(UrlContainer c) {
        save();
        if(System.currentTimeMillis() > 10)
            return null;
        
        return () -> {
            String urlString = c.getUrl();

            if(existingPens == null) {
                synchronized (Specials.class) {
                    if(existingPens == null) {
                        existingPens = Files.exists(getPath()) ? Stream.of(getPath().toFile().list())
                                .map(s -> s.substring(0, s.lastIndexOf('_')))
                                .collect(collectingAndThen(toSet(), ConcurrentSkipListSet::new)) : new ConcurrentSkipListSet<>();
                    }
                }
            }

            URL url = new URL(urlString.replace("/pen/", "/share/zip/"));
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.29 Safari/537.36");

            String name = con.getHeaderField("Content-Disposition");
            name = name.substring(22, name.length() - 5);

            if(existingPens.contains(name))
                return null;

            if(!dirCreated) {
                Files.createDirectories(getPath());
                dirCreated = true;
            }

            Path target = getPath().resolve(name+"_"+System.currentTimeMillis()+".zip");

            try(BufferHandler b = new BufferHandler()) {
                b.pipe(con.getInputStream(), target);
            }

            urlSucess(urlString, 1);
            return null;
        };
    }
    
    private volatile boolean saved = false;
    
    private  void save() {
        if(saved || urls.isEmpty()) return;
        
        synchronized(CodePen.class) {
            saved = true;
            
            try {
                Files.createDirectories(EnvConfig.DOWNLOAD_DIR);
                Files.write(EnvConfig.DOWNLOAD_DIR.resolve("codepen.urls.txt"), urls, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                logger.error("failed to create file: "+EnvConfig.DOWNLOAD_DIR.resolve("codepen.urls.txt"));
            }    
        }

        
    }
}
