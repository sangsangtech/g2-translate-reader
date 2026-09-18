package com.geeh.translatereader;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class TranslationTrackerTest {
    @Test public void keepsStableSentencesUntilVoiceAcceptsThemEvenAfterScrollingAway() {
        TranslationTracker t = new TranslationTracker(); t.update(List.of(), 0);
        t.update(List.of("First sentence.", "Second sentence."), 1);
        assertEquals(List.of("First sentence.", "Second sentence."), t.update(List.of("First sentence.", "Second sentence."), 801));
        assertEquals(List.of("First sentence.", "Second sentence."), t.update(List.of(), 1200));
        t.accepted("First sentence.");
        assertEquals(List.of("Second sentence."), t.update(List.of(), 1600));
        t.accepted("Second sentence."); assertTrue(t.pending().isEmpty());
    }
    @Test public void busyVoiceKeepsAllFiveSentencesInOrder() {
        TranslationTracker t = new TranslationTracker(); t.update(List.of(), 0);
        List<String> batch = List.of("One.", "Two.", "Three.", "Four.", "Five.");
        t.update(batch, 1); t.update(batch, 801);
        // The voice engine accepts three; the rest must survive without any reset.
        for (String text : batch.subList(0, 3)) t.accepted(text);
        assertEquals(List.of("Four.", "Five."), t.update(List.of(), 1200));
        t.accepted("Four."); t.accepted("Five.");
        assertTrue(t.update(batch, 2000).isEmpty());
    }
    @Test public void newSentenceSurvivesWhileAnotherIsWaitingOrPlaying() {
        TranslationTracker t = new TranslationTracker(); t.update(List.of(), 0);
        t.update(List.of("こんにちは。"), 1); t.update(List.of("こんにちは。"), 801);
        t.update(List.of("ありがとうございます。"), 900);
        assertEquals(List.of("こんにちは。", "ありがとうございます。"), t.update(List.of("ありがとうございます。"), 1700));
        t.accepted("こんにちは。");
        assertEquals(List.of("ありがとうございます。"), t.pending());
    }
    @Test public void growingEnglishRowReadsOnlyAddedCompleteSentence() {
        TranslationTracker t = new TranslationTracker(); deliver(t, List.of(), 0);
        deliver(t, List.of("Hello."), 1); deliver(t, List.of("Hello."), 801);
        deliver(t, List.of("Hello. Nice to meet you."), 1000);
        assertEquals(List.of("Nice to meet you."), deliver(t, List.of("Hello. Nice to meet you."), 1800));
        assertTrue(deliver(t, List.of("Hello. Nice to meet you."), 3000).isEmpty());
    }
    @Test public void growingJapaneseRowPreservesSuccessiveAdditions() {
        TranslationTracker t = new TranslationTracker(); deliver(t, List.of(), 0);
        deliver(t, List.of("こんにちは。"), 1); deliver(t, List.of("こんにちは。"), 801);
        deliver(t, List.of("こんにちは。お元気ですか？"), 900);
        assertEquals(List.of("お元気ですか?"), deliver(t, List.of("こんにちは。お元気ですか？"), 1700));
        deliver(t, List.of("こんにちは。お元気ですか？また会いましょう。"), 1800);
        assertEquals(List.of("また会いましょう。"), deliver(t, List.of("こんにちは。お元気ですか？また会いましょう。"), 2600));
    }
    @Test public void queuedPrefixAndItsNewSentenceRemainOrdered() {
        TranslationTracker t = new TranslationTracker(); t.update(List.of(), 0);
        t.update(List.of("Hello."), 1); t.update(List.of("Hello."), 801);
        t.update(List.of("Hello. Thank you."), 900);
        assertEquals(List.of("Hello.", "Thank you."), t.update(List.of("Hello. Thank you."), 1700));
        t.accepted("Hello."); t.accepted("Thank you."); assertTrue(t.pending().isEmpty());
    }
    @Test public void boundedBacklogRetainsExistingWorkAndRetriesVisibleOverflow() {
        TranslationTracker t = new TranslationTracker(); t.update(List.of(), 0);
        List<String> batch = java.util.stream.IntStream.range(0, 65).mapToObj(i -> "Sentence number " + i + ".").toList();
        t.update(batch, 1); t.update(batch, 801);
        assertEquals(64, t.pending().size()); assertTrue(t.backedUp());
        t.accepted(batch.get(0)); t.update(batch, 900);
        assertEquals(64, t.pending().size()); assertEquals(batch.get(64), t.pending().get(63));
    }
    @Test public void explicitResetClearsWaitingSentences() {
        TranslationTracker t = new TranslationTracker(); t.update(List.of(), 0);
        t.update(List.of("Hello."), 1); t.update(List.of("Hello."), 801);
        t.reset(); assertTrue(t.pending().isEmpty());
    }
    private List<String> deliver(TranslationTracker t, List<String> texts, long now) {
        List<String> ready = t.update(texts, now);
        for (String text : ready) t.accepted(text);
        return ready;
    }
    @Test public void skipsJapaneseFooterControls() {
        assertEquals(List.of("こんにちは。"), TranslationTracker.extract(List.of("KO>JA", "안녕하세요", "こんにちは。", "一時停止", "終了"), TranslationLanguage.JAPANESE));
    }
    @Test public void readsOnlyJapaneseOutgoingWithKoreanSource() {
        assertEquals(List.of("こんにちは。", "ありがとうございます。"), TranslationTracker.extract(List.of(
            "JA>KO", "お元気ですか。", "잘 지내세요?", "KO>JA", "안녕하세요", "こんにちは。",
            "고마워요", "ありがとうございます。", "일시 정지", "종료"), TranslationLanguage.JAPANESE));
    }
    @Test public void japaneseRequiresSourceAndRejectsOtherDirection() {
        assertTrue(TranslationTracker.extract(List.of("KO>JA", "こんにちは。", "JA>KO", "안녕하세요", "こんにちは。"), TranslationLanguage.JAPANESE).isEmpty());
    }
    @Test public void japaneseWrapsJoinWithoutInsertedSpaces() {
        assertEquals(List.of("コーヒーを一杯ください。"), TranslationTracker.extract(List.of("KO→JA", "커피 한 잔 주세요", "コーヒーを", "一杯ください。"), TranslationLanguage.JAPANESE));
    }
    @Test public void supportsJpAliasAndFullWidthMarkers() {
        assertEquals(List.of("東京"), TranslationTracker.extract(List.of("ＫＯ → ＪＰ", "도쿄", "東京"), TranslationLanguage.JAPANESE));
    }
    @Test public void languageSelectionNeverReadsTheOtherPair() {
        List<String> lines = List.of("KO>JA", "안녕", "こんにちは。", "KO>EN", "고마워", "Thank you.");
        assertEquals(List.of("こんにちは。"), TranslationTracker.extract(lines, TranslationLanguage.JAPANESE));
        assertEquals(List.of("Thank you."), TranslationTracker.extract(lines, TranslationLanguage.ENGLISH));
    }
    @Test public void japaneseDedupePreservesDifferentSentences() {
        TranslationTracker t = new TranslationTracker(); deliver(t, List.of(), 0);
        deliver(t, List.of("こんにちは。"), 1);
        assertEquals(List.of("こんにちは。"), deliver(t, List.of("こんにちは。"), 801));
        deliver(t, List.of("こんにちは。", "ありがとう。"), 1000);
        assertEquals(List.of("ありがとう。"), deliver(t, List.of("こんにちは。", "ありがとう。"), 1800));
        assertTrue(deliver(t, List.of("こんにちは。", "ありがとう。"), 3000).isEmpty());
    }
    @Test public void normalizesHalfWidthKatakanaAndRetainsOneCharacterAnswers() {
        TranslationTracker t = new TranslationTracker(); deliver(t, List.of(), 0);
        deliver(t, List.of("ｺｰﾋｰ。"), 1); assertEquals(List.of("コーヒー。"), deliver(t, List.of("コーヒー。"), 801));
        assertTrue(deliver(t, List.of("ｺｰﾋｰ。"), 2000).isEmpty());
        deliver(t, List.of("駅。"), 2001); assertEquals(List.of("駅。"), deliver(t, List.of("駅。"), 2801));
    }
    @Test public void skipsJapaneseVisibleHistoryAndPlaybackEcho() {
        TranslationTracker t = new TranslationTracker(); deliver(t, List.of("昨日の会話。"), 0);
        assertTrue(deliver(t, List.of("昨日の会話。"), 1000).isEmpty());
        deliver(t, List.of("こんにちは。"), 1100);
        assertEquals(List.of("こんにちは。"), deliver(t, List.of("こんにちは。"), 1900));
        assertTrue(deliver(t, List.of("こんにちは。"), 6000).isEmpty());
    }
    @Test public void readsOnlyOutgoingTranslations() {
        assertEquals(List.of("Hello. Nice to meet you."), TranslationTracker.extract(List.of("EN>KO", "How are you?", "안녕하세요?", "KO>EN", "안녕하세요. 만나서 반갑습니다.", "Hello.", "Nice to meet you.", "일시 정지", "종료")));
    }
    @Test public void skipsEnglishWithoutKoreanSource() { assertTrue(TranslationTracker.extract(List.of("KO>EN", "Hello.")).isEmpty()); }
    @Test public void joinsEnglishWrapsButSeparatesNextKoreanRow() {
        assertEquals(List.of("I want a cup of coffee.", "Thank you."), TranslationTracker.extract(List.of("KO→EN", "커피 한 잔 주세요", "I want a cup", "of coffee.", "고마워요", "Thank you.")));
    }
    @Test public void skipsAlreadyVisibleHistory() {
        TranslationTracker t = new TranslationTracker(); assertTrue(deliver(t, List.of("Old text."), 0).isEmpty()); assertTrue(deliver(t, List.of("Old text."), 3000).isEmpty());
    }
    @Test public void waitsForStableTextAndDoesNotRepeat() {
        TranslationTracker t = new TranslationTracker(); deliver(t, List.of(), 0);
        assertTrue(deliver(t, List.of("Hello."), 10).isEmpty()); assertTrue(deliver(t, List.of("Hello there."), 500).isEmpty());
        assertEquals(List.of("Hello there."), deliver(t, List.of("Hello there."), 1300));
        assertTrue(deliver(t, List.of("Hello there."), 3000).isEmpty());
    }
    @Test public void unpunctuatedPhraseGetsLongerSettle() {
        TranslationTracker t = new TranslationTracker(); deliver(t, List.of(), 0); deliver(t, List.of("Good morning"), 1);
        assertTrue(deliver(t, List.of("Good morning"), 1000).isEmpty()); assertEquals(List.of("Good morning"), deliver(t, List.of("Good morning"), 1501));
    }
    @Test public void dedupesSameTextWithoutDiscardingNewSpeech() {
        TranslationTracker t = new TranslationTracker(); deliver(t, List.of(), 0);
        assertTrue(deliver(t, List.of("Echo voice."), 100).isEmpty());
        assertEquals(List.of("Echo voice."), deliver(t, List.of("Echo voice."), 900));
        assertTrue(deliver(t, List.of("Echo voice."), 1500).isEmpty()); deliver(t, List.of("New sentence."), 1600);
        assertEquals(List.of("New sentence."), deliver(t, List.of("New sentence."), 2400));
    }
    @Test public void retainsChangedSentenceInsteadOfDiscardingSimilarPrefix() {
        TranslationTracker t = new TranslationTracker(); deliver(t, List.of(), 0); deliver(t, List.of("Hello nice to meet you."), 1); deliver(t, List.of("Hello nice to meet you."), 1000);
        assertTrue(deliver(t, List.of("Hello nice to meet you today."), 2000).isEmpty()); assertEquals(List.of("Hello nice to meet you today."), deliver(t, List.of("Hello nice to meet you today."), 4000));
    }
}
