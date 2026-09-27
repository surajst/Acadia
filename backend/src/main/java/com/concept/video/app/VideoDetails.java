package com.concept.video.app;

/**
 * What YouTube says about a video: its real title and a thumbnail.
 *
 * <p>Taken from oEmbed rather than from the teacher, so a class list cannot be
 * given a name that does not match what plays.
 */
public record VideoDetails(String title, String thumbnailUrl) {}
