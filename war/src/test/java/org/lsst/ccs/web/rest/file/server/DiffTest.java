package org.lsst.ccs.web.rest.file.server;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.algorithm.DiffException;
import com.github.difflib.patch.Patch;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 *
 * @author tonyj
 */
public class DiffTest {
    @Test
    public void diffTest() throws DiffException {
        List<String> lines1 = List.of("This is a test file","with two lines");
        List<String> lines2 = List.of("This is some new content");
        Patch<String> diff = DiffUtils.diff(lines1, lines2);
        List<String> diffList = UnifiedDiffUtils.generateUnifiedDiff("file1", "file2", lines1, diff, 2);
        assertEquals(6, diffList.size());
        
        Patch<String> diff2 = DiffUtils.diff(lines2, lines1);
        List<String> diffList2 = UnifiedDiffUtils.generateUnifiedDiff("file2", "file1", lines2, diff2, 2);
        assertEquals(6, diffList2.size());
    }
}
