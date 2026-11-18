package com.mycar.launcher;

/** Один файл в очереди воспроизведения. */
final class Track {
    final String path;
    final String name;
    final boolean video;

    Track(String path, String name, boolean video) {
        this.path = path;
        this.name = name;
        this.video = video;
    }
}
