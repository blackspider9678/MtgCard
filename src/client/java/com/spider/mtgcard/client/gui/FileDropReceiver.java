package com.spider.mtgcard.client.gui;

import java.nio.file.Path;
import java.util.List;

public interface FileDropReceiver {
    void onFilesDropped(List<Path> paths);
}
