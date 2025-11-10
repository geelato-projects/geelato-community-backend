package cn.geelato.web.platform.graal;

import cn.geelato.web.platform.srv.script.GraalUse;
import lombok.Getter;
import lombok.Setter;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;


public class GraalContext {
    public static Context getContext() {
        return Context.newBuilder(GraalUse.Language_JS)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true).build();
    }
}
