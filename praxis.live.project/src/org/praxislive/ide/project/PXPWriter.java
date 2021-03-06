/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2017 Neil C Smith.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details.
 *
 * You should have received a copy of the GNU General Public License version 3
 * along with this work; if not, see http://www.gnu.org/licenses/
 *
 *
 * Please visit http://neilcsmith.net if you need additional information or
 * have any questions.
 */

package org.praxislive.ide.project;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import org.praxislive.ide.project.api.ExecutionLevel;
import org.praxislive.ide.project.api.PraxisProjectProperties;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Neil C Smith (http://neilcsmith.net)
 */
public class PXPWriter {
    
    

    private final FileObject projectDir;
    private final FileObject projectFile;
    private final PraxisProjectProperties props;

    private PXPWriter(FileObject projectDir, FileObject projectFile,
            PraxisProjectProperties props) {
        this.projectDir = projectDir;
        this.projectFile = projectFile;
        this.props = props;
    }

    private void write() throws IOException {
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(projectFile.getOutputStream());
            writeConfig(writer);
            writeLevel(writer, ExecutionLevel.BUILD);
            for (FileObject file : props.getFiles(ExecutionLevel.BUILD)) {
                writeFile(writer, file);
            }
            writeLevel(writer, ExecutionLevel.RUN);
            for (FileObject file : props.getFiles(ExecutionLevel.RUN)) {
                writeFile(writer, file);
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
    
    private void writeConfig(Writer writer) throws IOException {
        writer.write(DefaultPraxisProject.LIBS_COMMAND);
        writer.write("\n");
    }
    
    private void writeLevel(Writer writer, ExecutionLevel level) throws IOException {
        writer.write("\n# ");
        if (level == ExecutionLevel.BUILD) {
            writer.write(PXPReader.BUILD_LEVEL_SWITCH);
        } else {
            writer.write(PXPReader.RUN_LEVEL_SWITCH);
        }
        writer.write("\n");
    }

    private void writeFile(Writer writer, FileObject file) throws IOException {
        String path = FileUtil.getRelativePath(projectDir, file);
        if (path == null) {
            path = file.getPath();
        }
        writer.write(PXPReader.INCLUDE_CMD);
        writer.write(" [");
        writer.write(PXPReader.FILE_CMD);
        writer.write(" \"");
        writer.write(path);
        writer.write("\"]\n");
    }

    public static void writeProjectProperties(FileObject projectDir,
            FileObject file,
            PraxisProjectProperties props) throws IOException {
            new PXPWriter(projectDir, file, props).write();
    }

}
