package com.odk.dto;

public class StatsParTypeDTO {
    private long tailleRapport;
    private long tailleImage;
    private long tailleVideo;

    public StatsParTypeDTO(long tailleRapport, long tailleImage, long tailleVideo) {
        this.tailleRapport = tailleRapport;
        this.tailleImage = tailleImage;
        this.tailleVideo = tailleVideo;
    }

    public long getTailleRapport() { return tailleRapport; }
    public void setTailleRapport(long tailleRapport) { this.tailleRapport = tailleRapport; }

    public long getTailleImage() { return tailleImage; }
    public void setTailleImage(long tailleImage) { this.tailleImage = tailleImage; }

    public long getTailleVideo() { return tailleVideo; }
    public void setTailleVideo(long tailleVideo) { this.tailleVideo = tailleVideo; }
}
