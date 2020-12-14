package org.lsst.ccs.rest.file.server.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author tonyj
 */
class Utils {

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[32768];
        for (;;) {
            int l = in.read(buf);
            if (l < 0) {
                break;
            }
            out.write(buf, 0, l);
        }
    }

    static OpenOptionsBuilder openOptionsBuilder() {
        return new OpenOptionsBuilder();
    }

    static class OpenOptionsBuilder {
        private final List<OpenOption> options = new ArrayList<>();
        
        void add(OpenOption option) {
            options.add(option);
        }
        
        OpenOption[] build() {
            return options.toArray(new OpenOption[options.size()]);
        }
    }
    
}
