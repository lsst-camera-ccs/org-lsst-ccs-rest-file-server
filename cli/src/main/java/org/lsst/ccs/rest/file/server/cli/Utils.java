package org.lsst.ccs.rest.file.server.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility helpers for the command line interface.
 *
 * @author tonyj
 */
class Utils {

    /**
     * Copies all bytes from an input stream to an output stream.
     *
     * @param in the source stream
     * @param out the destination stream
     * @throws IOException if an I/O error occurs during the copy
     */
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

    /**
     * Creates a builder for an array of {@link OpenOption} values.
     *
     * @param options initial open options
     * @return a new builder containing the provided options
     */
    static OpenOptionsBuilder openOptionsBuilder(OpenOption... options) {
        return new OpenOptionsBuilder(options);
    }

    /**
     * Helper class for building arrays of {@link OpenOption} values.
     */
    static class OpenOptionsBuilder {
        private final List<OpenOption> options;

        private OpenOptionsBuilder(OpenOption[] initialOptions) {
            options = new ArrayList<>(Arrays.asList(initialOptions));
        }

        /**
         * Adds an option to the builder.
         *
         * @param option the option to add
         */
        void add(OpenOption option) {
            options.add(option);
        }

        /**
         * Builds the array of open options.
         *
         * @return the accumulated options
         */
        OpenOption[] build() {
            return options.toArray(new OpenOption[options.size()]);
        }
    }

}
