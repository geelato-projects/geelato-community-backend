package cn.geelato.web.platform.srv.platform.service;

import cn.geelato.meta.AppMultiLang;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.handler.FileHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Component
public class AppMultiLangService extends BaseService {
    @Lazy
    @Autowired
    private FileHandler fileHandler;

    public String queryAppMultiLang(String appId, String langType, String purpose) throws IOException {
        BufferedReader bufferedReader = null;
        try {
            Map<String, Object> params = Map.of("appId", appId, "langType", langType, "purpose", purpose);
            List<AppMultiLang> appMultiLangs = queryModel(AppMultiLang.class, params, "update_at DESC");
            if (!appMultiLangs.isEmpty()) {
                AppMultiLang model = appMultiLangs.get(0);
                if (model != null && StringUtils.isNotBlank(model.getLangPackage())) {
                    File file = fileHandler.toFile(model.getLangPackage());
                    if (file.exists()) {
                        StringBuilder contentBuilder = new StringBuilder();
                        bufferedReader = Files.newBufferedReader(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8);
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            contentBuilder.append(line);
                        }
                        return contentBuilder.toString();
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        }
    }
}
