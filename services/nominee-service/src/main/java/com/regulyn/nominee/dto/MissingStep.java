package com.regulyn.nominee.dto;

public class MissingStep {

    private String step;
    private int requiredMinDocs;
    private int currentDocs;

    public MissingStep() {
    }

    public MissingStep(String step, int requiredMinDocs, int currentDocs) {
        this.step = step;
        this.requiredMinDocs = requiredMinDocs;
        this.currentDocs = currentDocs;
    }

    public String getStep() {
        return step;
    }

    public void setStep(String step) {
        this.step = step;
    }

    public int getRequiredMinDocs() {
        return requiredMinDocs;
    }

    public void setRequiredMinDocs(int requiredMinDocs) {
        this.requiredMinDocs = requiredMinDocs;
    }

    public int getCurrentDocs() {
        return currentDocs;
    }

    public void setCurrentDocs(int currentDocs) {
        this.currentDocs = currentDocs;
    }
}
