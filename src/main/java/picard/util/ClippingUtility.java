/*
 * The MIT License
 *
 * Copyright (c) 2011 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package picard.util;

import htsjdk.samtools.ReservedTagConstants;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.samtools.util.Tuple;
import picard.PicardException;

/**
 * Utilities to clip the adapter sequence from a SAMRecord read
 *
 * @author Tim Fennell
 */
public class ClippingUtility {

    /**
     * The default value used for the minimum number of contiguous bases to match against.
     */
    public static final int MIN_MATCH_BASES = 12;
    /**
     * The default value used for the minimum number of contiguous bases to match against in a paired end read
     */
    public static final int MIN_MATCH_PE_BASES = 6;

    /**
     * The default value used for the maximum error rate when matching read bases to clippable sequence.
     */
    public static final double MAX_ERROR_RATE = 0.10;
    /**
     * The default value used for the maximum error rate when matching paired end read bases to clippable sequence.
     */
    public static final double MAX_PE_ERROR_RATE = 0.10;

    /**
     * The value returned by methods returning int when no match is found.
     */
    public static final int NO_MATCH = -1;

    private static final Log log = Log.getInstance(ClippingUtility.class);

    /**
     * @deprecated Use the varargs version.  This no longer returns a warning string..
     */
    public static void adapterTrimIlluminaSingleRead(final SAMRecord read, final AdapterPair adapter) {
        adapterTrimIlluminaSingleRead(read, MIN_MATCH_BASES, MAX_ERROR_RATE, adapter);
    }

    /**
     * @deprecated Use the varargs version.  This no longer returns a warning string..
     */
    public static void adapterTrimIlluminaSingleRead(final SAMRecord read, final AdapterPair adapter,
                                                     final int minMatchBases, final double maxErrorRate) {
        adapterTrimIlluminaSingleRead(read, minMatchBases, maxErrorRate, adapter);
    }

    /**
     * Invokes adapterTrimIlluminRead with default parameters for a single read.
     * If the read is a negative strand, its bases will be reverse complemented
     * Simpler, more common of two overloads. Accepts multiple adapters
     * and tries them all until it finds the first one that matches.
     *
     * @param read     SAM/BAM read to trim
     * @param adapters which adapters to try to use (indexed, paired_end, or single_end)
     * @return AdapterPair    the AdapterPair matched, or null
     */
    public static AdapterPair adapterTrimIlluminaSingleRead(final SAMRecord read, final AdapterPair... adapters) {
        return adapterTrimIlluminaSingleRead(read, MIN_MATCH_BASES, MAX_ERROR_RATE, adapters);
    }

    /**
     * Invokes adapterTrimIlluminRead with explicit matching thresholds for a single read.
     * If the read is a negative strand, a copy of its bases will be reverse complemented.
     * More general form of the two overloads. Accepts multiple adapters
     * and tries them all until it finds the first one that matches.
     *
     * @param read          SAM/BAM read to trim
     * @param minMatchBases minimum number of contiguous bases to match against in a read
     * @param maxErrorRate  maximum error rate when matching read bases
     * @param adapters      which adapters to try (indexed, paired_end, or single_end)
     * @return AdapterPair    the AdapterPair matched, or null
     */
    public static AdapterPair adapterTrimIlluminaSingleRead(final SAMRecord read, final int minMatchBases,
                                                            final double maxErrorRate, final AdapterPair... adapters) {
        for (AdapterPair adapter : adapters) {
            final int indexOfAdapterSequence = findIndexOfClipSequence(
                    getReadBases(read), adapter.get3PrimeAdapterBytes(), minMatchBases, maxErrorRate);
            if (indexOfAdapterSequence != NO_MATCH) {
                // Convert to a one-based index for storage on the record.
                read.setAttribute(ReservedTagConstants.XT, indexOfAdapterSequence + 1);
                return adapter;
            }
        }
        return null;
    }

    /**
     * Return the adapter to be trimmed from a read represented as an array of bytes[]
     * @param read The byte array of read data
     * @param minMatchBases The minimum number of base matches required for adapter matching
     * @param maxErrorRate The maximum error rate allowed for adapter matching
     * @param templateIndex The paired index of the reads (1 or 2, 1 for single ended reads)
     * @param adapters The set of adapters to search for
     * @return The adapter pair that matched the read and its index in the read or null
     */
    public static Tuple<AdapterPair, Integer> findAdapterPairAndIndexForSingleRead(final byte[] read,
                                                                                   final int minMatchBases,
                                                                                   final double maxErrorRate,
                                                                                   final int templateIndex,
                                                                                   final AdapterPair... adapters) {
        for (AdapterPair adapter : adapters) {
            int indexOfAdapterSequence;
            if (templateIndex == 1) {
                indexOfAdapterSequence = findIndexOfClipSequence(
                        read, adapter.get3PrimeAdapterBytes(), minMatchBases, maxErrorRate);
            } else if (templateIndex == 2) {
                indexOfAdapterSequence = findIndexOfClipSequence(
                        read, adapter.get5PrimeAdapterBytesInReadOrder(), minMatchBases, maxErrorRate);
            } else {
                throw new PicardException("Read template index must be 1 or 2");
            }
            if (indexOfAdapterSequence != NO_MATCH) {
                // Convert to a one-based index for storage on the read.
                return new Tuple<>(adapter, indexOfAdapterSequence);
            }
        }
        return null;
    }

    /**
     * @deprecated Use the varargs version.  This no longer returns a warning string..
     */
    public static String adapterTrimIlluminaPairedReads(final SAMRecord read1, final SAMRecord read2, final AdapterPair adapters) {
        adapterTrimIlluminaPairedReads(read1, read2, MIN_MATCH_PE_BASES, MAX_PE_ERROR_RATE, adapters);
        return null;
    }

    /**
     * @deprecated Use the varargs version.  This no longer returns a warning string..
     */
    public static String adapterTrimIlluminaPairedReads(final SAMRecord read1, final SAMRecord read2,
                                                        final AdapterPair adapters, final int minMatchBases, final double maxErrorRate) {

        adapterTrimIlluminaPairedReads(read1, read2, minMatchBases, maxErrorRate, adapters);
        return null;
    }

    /**
     * Invokes adapterTrimIlluminaPairedReads with default less stringent parameters for a pair of reads.
     * If the read is a negative strand, its bases will be reverse complemented
     * Simpler, more common of two overloads.
     *
     * @param read1    first read of the pair
     * @param read2    second read of the pair
     * @param adapters which adapters to use (indexed, paired_end, or single_end, nextera), attempted in order
     * @return int     number of bases trimmed
     */
    public static AdapterPair adapterTrimIlluminaPairedReads(final SAMRecord read1, final SAMRecord read2, final AdapterPair... adapters) {
        return adapterTrimIlluminaPairedReads(read1, read2, MIN_MATCH_PE_BASES, MAX_PE_ERROR_RATE, adapters);
    }

    /**
     * Invokes adapterTrimIlluminaRead with explicit parameters for a pair of reads.
     * More general form of two overloads.
     * Returns a warning string when the trim positions found differed for each read.
     *
     * @param read1         first read of the pair.
     *                      If read1 is a negative strand, a copy of its bases will be reverse complemented.
     * @param read2         second read of the pair.
     *                      If read2 is a negative strand, a copy of its bases will be reverse complemented
     * @param minMatchBases minimum number of contiguous bases to match against in a read
     * @param maxErrorRate  maximum error rate when matching read bases
     * @param adapters      which adapters to use (indexed, paired_end, or single_end, nextera), attempted in order
     * @return int     number of bases trimmed
     */
    public static AdapterPair adapterTrimIlluminaPairedReads(final SAMRecord read1, final SAMRecord read2,
                                                             final int minMatchBases, final double maxErrorRate, final AdapterPair... adapters) {
        AdapterPair matched = null;

        for (final AdapterPair adapterPair : adapters) {
            final int index1 = findIndexOfClipSequence(
                    getReadBases(read1), adapterPair.get3PrimeAdapterBytes(), minMatchBases, maxErrorRate);
            final int index2 = findIndexOfClipSequence(
                    getReadBases(read2), adapterPair.get5PrimeAdapterBytesInReadOrder(), minMatchBases, maxErrorRate);

            if (index1 == index2) {
                if (index1 != NO_MATCH) {
                    // This is the best result: both match exactly, we're done
                    read1.setAttribute(ReservedTagConstants.XT, index1 + 1);
                    read2.setAttribute(ReservedTagConstants.XT, index2 + 1);
                    return adapterPair;
                } else {
                    // Otherwise they were both no match, we just keep trying
                }
            } else if (index1 == NO_MATCH || index2 == NO_MATCH) {
                // One of them matched, but the other didn't.
                // Try matching the one that did match again with a little tighter
                // stringency and, if that works, trim both reads at the matching point.
                // This is only the second-best possibility... keep looking for a perfect match.
                if (attemptOneSidedMatch(read1, read2, index1, index2, 2 * minMatchBases)) {
                    matched = adapterPair;
                }

            } else {
                // Both matched at different positions. Do nothing
            }
        }


        return matched;
    }

    /**
     * When an adapter is matched in only one end of a pair, we check it again with
     * stricter thresholds.  If it still matches, then we trim both ends of the read
     * at the same location.
     */
    private static boolean attemptOneSidedMatch(final SAMRecord read1,
                                                final SAMRecord read2,
                                                final int index1,
                                                final int index2,
                                                final int stricterMinMatchBases) {

        // Save all the data about the read where we found the adapter match
        final int matchedIndex = index1 == NO_MATCH ? index2 : index1;
        final SAMRecord matchedRead = index1 == NO_MATCH ? read2 : read1;


        // If it still matches with a stricter minimum matched bases, then
        // clip both reads
        if (matchedRead.getReadLength() - matchedIndex >= stricterMinMatchBases) {
            if (read1.getReadBases().length > matchedIndex) {
                read1.setAttribute(ReservedTagConstants.XT, matchedIndex + 1);
            }
            if (read2.getReadBases().length > matchedIndex) {
                read2.setAttribute(ReservedTagConstants.XT, matchedIndex + 1);
            }
            return true;
        }
        return false;
    }

    /**
     * Returns an array of bytes representing the bases in the read,
     * reverse complementing them if the read is on the negative strand
     */
    private static byte[] getReadBases(final SAMRecord read) {
        if (!read.getReadNegativeStrandFlag()) {
            return read.getReadBases();
        } else {
            final byte[] reverseComplementedBases = new byte[read.getReadBases().length];
            System.arraycopy(read.getReadBases(), 0, reverseComplementedBases, 0, reverseComplementedBases.length);
            SequenceUtil.reverseComplement(reverseComplementedBases);
            return reverseComplementedBases;
        }
    }

    /**
     * Finds the first index of the adapterSequence sequence in the read sequence requiring at least minMatch
     * bases of pairwise alignment with a maximum number of errors dictated by maxErrorRate.
     *
     * @param read
     */
    public static int findIndexOfClipSequence(final byte[] read, final byte[] adapterSequence, final int minMatch, final double maxErrorRate) {
        // If the read's too short we can't possibly match it
        if (read == null || read.length < minMatch) return NO_MATCH;
        final int minClipPosition = 0;

        // Walk backwards down the read looking for the sequence
        READ_LOOP:
        for (int start = read.length - minMatch; start > minClipPosition - 1; --start) {
            final int length = Math.min(read.length - start, adapterSequence.length);
            final int mismatchesAllowed = (int) (length * maxErrorRate);
            int mismatches = 0;

            for (int i = 0; i < length; ++i) {
                if (!SequenceUtil.isNoCall(adapterSequence[i]) &&
                        !SequenceUtil.basesEqual(adapterSequence[i], read[start + i]) &&
                        ++mismatches > mismatchesAllowed) {
                    continue READ_LOOP;
                }
            }

            // If we got this far without breaking out, then it matches
            return start;
        }

        return NO_MATCH;
    }
}
