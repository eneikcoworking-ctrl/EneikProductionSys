package com.eneik.production.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class EpistemicMetadataClassifierTest {

    private final EpistemicMetadataClassifier classifier = new EpistemicMetadataClassifier();

    @Test
    void emptyInputExtractsNothingRatherThanGuessingAMiddleValue() {
        EpistemicMetadataClassifier.Classification c = classifier.classify(null, "", "   ");
        assertNull(c.cynefinDomain());
        assertNull(c.kanoClass());
        assertFalse(c.anythingExtracted());
    }

    @Test
    void textWithNoRecognisedMarkerExtractsNothing() {
        EpistemicMetadataClassifier.Classification c = classifier.classify("something something");
        assertNull(c.cynefinDomain());
        assertNull(c.kanoClass());
    }

    @Test
    void recognisesTheAxesInEnglish() {
        assertEquals("complicated", classifier.classify("Build the API integration and its migration").cynefinDomain());
        assertEquals("complex", classifier.classify("Research and prototype a recommender").cynefinDomain());
        assertEquals("chaotic", classifier.classify("Production outage, the service crashes").cynefinDomain());
        assertEquals("must-be", classifier.classify("login and password reset with access control").kanoClass());
        assertEquals("one-dimensional", classifier.classify("make the search filter faster").kanoClass());
    }

    @Test
    void recognisesTheAxesInRussianToo() {
        // Client wishes in this factory are measured to arrive in Russian as often as in English.
        assertEquals("complicated", classifier.classify("Нужна интеграция и миграция схемы").cynefinDomain());
        assertEquals("must-be", classifier.classify("Добавить вход и авторизацию по паролю").kanoClass());
        assertEquals("chaotic", classifier.classify("Срочно: сервис падает, авария").cynefinDomain());
    }

    @Test
    void classificationIsCaseInsensitiveAndReadsEveryFragment() {
        EpistemicMetadataClassifier.Classification c =
                classifier.classify("nothing here", null, "PASSWORD RESET", "ARCHITECTURE rework");
        assertEquals("must-be", c.kanoClass());
        assertEquals("complicated", c.cynefinDomain());
    }
}
