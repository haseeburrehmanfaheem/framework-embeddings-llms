package com.aacr.wala.workshop.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileUtils {
    public static String findFileNameFromParentPath(String inPath, String filenameWithExt) throws IOException {
        if (inPath.endsWith(filenameWithExt))
            return inPath;

        File inFile = new File(inPath);
        if (!inFile.isDirectory())
            return null;

        List<Path> candidates = Files.find(
                Paths.get(inFile.getAbsolutePath()),
                Integer.MAX_VALUE,
                (filePath, fileAttr) -> filePath.getFileName().toString().endsWith(filenameWithExt)
        ).collect(Collectors.toList());

        if (!candidates.isEmpty())
            return candidates.get(0).toAbsolutePath().toString();
        else
            return null;
    }

    public static List<Path> retrievePathsMatchingExtFromParent(String parentPath, String extToMatch) throws IOException {
        Stream<Path> opStream = Files.find(Paths.get(parentPath),
                Integer.MAX_VALUE,
                (filePath, fileAttr) -> filePath.getFileName().toString().endsWith(extToMatch));

        return opStream.collect(Collectors.toList());
    }
}
