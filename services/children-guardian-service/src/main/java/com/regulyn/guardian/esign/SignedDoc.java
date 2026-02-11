package com.regulyn.guardian.esign;

public record SignedDoc(byte[] bytes, String mime, String filename) {
}
