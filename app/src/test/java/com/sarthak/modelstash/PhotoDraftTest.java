package com.sarthak.modelstash;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.sarthak.modelstash.data.ModelPhoto;
import com.sarthak.modelstash.util.PhotoDraft;
import com.sarthak.modelstash.util.PhotoLabel;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PhotoDraftTest {

    private static PhotoDraft draftOf(String... paths) {
        PhotoDraft draft = new PhotoDraft();
        for (String path : paths) {
            draft.add(path);
        }
        return draft;
    }

    @Test
    public void holdsAtMostSixPhotosAndNoDuplicates() {
        PhotoDraft draft = draftOf("a", "b", "c", "d", "e");
        assertEquals(1, draft.remaining());
        assertFalse(draft.add("a"));   // already there
        assertTrue(draft.add("f"));
        assertTrue(draft.isFull());
        assertFalse(draft.add("g"));   // seventh is refused
        assertEquals(6, draft.size());
    }

    @Test
    public void firstPhotoIsTheCoverAndMakeCoverMovesItToTheFront() {
        PhotoDraft draft = draftOf("a", "b", "c", "d");
        assertEquals("a", draft.coverPath());
        draft.makeCover(2);
        assertEquals(Arrays.asList("c", "a", "b", "d"), draft.paths());
        assertNull(new PhotoDraft().coverPath());
    }

    @Test
    public void dragMovesShiftTheOthers() {
        PhotoDraft draft = draftOf("a", "b", "c", "d");
        draft.move(0, 3);
        assertEquals(Arrays.asList("b", "c", "d", "a"), draft.paths());
        draft.move(3, 1);
        assertEquals(Arrays.asList("b", "a", "c", "d"), draft.paths());
    }

    @Test
    public void removingTheCoverPromotesTheNextPhoto() {
        PhotoDraft draft = draftOf("a", "b", "c");
        assertEquals("a", draft.remove(0));
        assertEquals("b", draft.coverPath());
        assertEquals(4, draft.remaining());
    }

    @Test
    public void toPhotosNumbersPositionsAndKeepsLabels() {
        PhotoDraft draft = new PhotoDraft();
        draft.add("box.jpg", PhotoLabel.BOX_ART);
        draft.add("built.jpg", PhotoLabel.FINISHED_BUILD);
        draft.makeCover(1);
        List<ModelPhoto> rows = draft.toPhotos();
        assertEquals("built.jpg", rows.get(0).path);
        assertEquals(0, rows.get(0).position);
        assertEquals(PhotoLabel.FINISHED_BUILD, rows.get(0).label);
        assertEquals(1, rows.get(1).position);
        assertEquals(PhotoLabel.BOX_ART, rows.get(1).label);
    }

    @Test
    public void survivesRotationThroughPlainArrays() {
        PhotoDraft draft = new PhotoDraft();
        draft.add("a", PhotoLabel.BOX_ART);
        draft.add("b", PhotoLabel.FINISHED_BUILD);
        PhotoDraft restored = new PhotoDraft();
        restored.restore(draft.paths(), draft.labels());
        assertEquals(draft.paths(), restored.paths());
        assertArrayEquals(draft.labels(), restored.labels());
        restored.restore(null, null);
        assertTrue(restored.isEmpty());
    }

    @Test
    public void newPhotosStartUnlabelledUntilYouChoose() {
        PhotoDraft draft = new PhotoDraft();
        draft.add("a");
        draft.add("b");
        assertEquals(PhotoLabel.NONE, draft.get(0).label);
        assertFalse(PhotoLabel.isSet(draft.get(1).label));
        draft.setLabel(1, PhotoLabel.FINISHED_BUILD);
        assertEquals(PhotoLabel.FINISHED_BUILD, draft.get(1).label);
        draft.setLabel(1, PhotoLabel.NONE);              // and you can clear it again
        assertEquals(PhotoLabel.NONE, draft.get(1).label);
    }

    @Test
    public void unknownLabelsCountAsUnlabelled() {
        assertEquals(PhotoLabel.NONE, PhotoLabel.clamp(7));
        assertEquals(PhotoLabel.NONE, PhotoLabel.clamp(-5));
        assertEquals(PhotoLabel.OTHER, PhotoLabel.clamp(PhotoLabel.OTHER));
        PhotoDraft draft = new PhotoDraft();
        draft.add("a", 42);
        assertEquals(PhotoLabel.NONE, draft.get(0).label);
        assertEquals(PhotoLabel.NONE, new ModelPhoto().label);  // a fresh row is unlabelled too
    }
}
