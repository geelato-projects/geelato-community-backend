package cn.geelato.web.platform.boot;

import cn.geelato.core.GlobalContext;
import cn.geelato.web.platform.graal.GraalContext;
import cn.geelato.web.platform.srv.script.GraalUse;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class GraalConfiguration extends GlobalContext{
    @Bean
    @Scope("prototype")
    public Context Context() {
        if (GlobalContext.__POLYGLOT_DEBUGGER__) {
            String port = "4242";
            String path = "polyglot_debugger";
            String hostAddress = "localhost";
            String url = String.format(
                    "chrome-devtools://devtools/bundled/js_app.html?ws=%s:%s/%s",
                    hostAddress, port, path);
            System.out.println(url);
            return Context.newBuilder(GraalUse.Language_JS)
                    .allowHostAccess(HostAccess.ALL)
                    .allowHostClassLookup(className -> true)
                    .option("inspect", port)
                    .option("inspect.Path", path)
                    .build();
        } else {
            return Context.newBuilder(GraalUse.Language_JS)
                    .allowHostAccess(HostAccess.ALL)
                    .allowHostClassLookup(className -> true)
                    .build();
        }
    }
}
