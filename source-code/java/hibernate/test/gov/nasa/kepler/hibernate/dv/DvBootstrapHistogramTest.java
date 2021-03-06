/*
 * Copyright 2017 United States Government as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All Rights Reserved.
 * 
 * This file is available under the terms of the NASA Open Source Agreement
 * (NOSA). You should have received a copy of this agreement with the
 * Kepler source code; see the file NASA-OPEN-SOURCE-AGREEMENT.doc.
 * 
 * No Warranty: THE SUBJECT SOFTWARE IS PROVIDED "AS IS" WITHOUT ANY
 * WARRANTY OF ANY KIND, EITHER EXPRESSED, IMPLIED, OR STATUTORY,
 * INCLUDING, BUT NOT LIMITED TO, ANY WARRANTY THAT THE SUBJECT SOFTWARE
 * WILL CONFORM TO SPECIFICATIONS, ANY IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, OR FREEDOM FROM
 * INFRINGEMENT, ANY WARRANTY THAT THE SUBJECT SOFTWARE WILL BE ERROR
 * FREE, OR ANY WARRANTY THAT DOCUMENTATION, IF PROVIDED, WILL CONFORM
 * TO THE SUBJECT SOFTWARE. THIS AGREEMENT DOES NOT, IN ANY MANNER,
 * CONSTITUTE AN ENDORSEMENT BY GOVERNMENT AGENCY OR ANY PRIOR RECIPIENT
 * OF ANY RESULTS, RESULTING DESIGNS, HARDWARE, SOFTWARE PRODUCTS OR ANY
 * OTHER APPLICATIONS RESULTING FROM USE OF THE SUBJECT SOFTWARE.
 * FURTHER, GOVERNMENT AGENCY DISCLAIMS ALL WARRANTIES AND LIABILITIES
 * REGARDING THIRD-PARTY SOFTWARE, IF PRESENT IN THE ORIGINAL SOFTWARE,
 * AND DISTRIBUTES IT "AS IS."
 * 
 * Waiver and Indemnity: RECIPIENT AGREES TO WAIVE ANY AND ALL CLAIMS
 * AGAINST THE UNITED STATES GOVERNMENT, ITS CONTRACTORS AND
 * SUBCONTRACTORS, AS WELL AS ANY PRIOR RECIPIENT. IF RECIPIENT'S USE OF
 * THE SUBJECT SOFTWARE RESULTS IN ANY LIABILITIES, DEMANDS, DAMAGES,
 * EXPENSES OR LOSSES ARISING FROM SUCH USE, INCLUDING ANY DAMAGES FROM
 * PRODUCTS BASED ON, OR RESULTING FROM, RECIPIENT'S USE OF THE SUBJECT
 * SOFTWARE, RECIPIENT SHALL INDEMNIFY AND HOLD HARMLESS THE UNITED
 * STATES GOVERNMENT, ITS CONTRACTORS AND SUBCONTRACTORS, AS WELL AS ANY
 * PRIOR RECIPIENT, TO THE EXTENT PERMITTED BY LAW. RECIPIENT'S SOLE
 * REMEDY FOR ANY SUCH MATTER SHALL BE THE IMMEDIATE, UNILATERAL
 * TERMINATION OF THIS AGREEMENT.
 */

package gov.nasa.kepler.hibernate.dv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the {@link DvBootstrapHistogram} class.
 * 
 * @author Bill Wohler
 */
public class DvBootstrapHistogramTest {

    private static final Log log = LogFactory.getLog(DvBootstrapHistogramTest.class);

    private static final float STATISTIC = 4.1F;
    private static final float PROBABILITY = 4.2F;
    private static final int LENGTH = 42;
    static final int FINAL_SKIP_COUNT = 43;

    private DvBootstrapHistogram bootstrapHistogram;

    @Before
    public void createExpectedBootstrapHistogram() {
        bootstrapHistogram = createBootstrapHistogram(STATISTIC, PROBABILITY,
            LENGTH, FINAL_SKIP_COUNT);
    }

    private DvBootstrapHistogram createBootstrapHistogram(float statistic,
        float probability, int length, int finalSkipCount) {

        List<Float> statistics = new ArrayList<Float>(length);
        List<Float> probabilities = new ArrayList<Float>(length);
        for (int i = 0; i < length; i++) {
            statistics.add(statistic);
            probabilities.add(probability);
        }

        return new DvBootstrapHistogram(statistics, probabilities,
            finalSkipCount);
    }

    @Test
    public void testConstructor() {
        // Create simply to get code coverage.
        new DvBootstrapHistogram();

        List<Float> statistics = bootstrapHistogram.getStatistics();
        assertNotNull("values", statistics);
        assertEquals(LENGTH, statistics.size());
        assertEquals(STATISTIC, statistics.get(0)
            .floatValue(), 0);

        List<Float> probabilities = bootstrapHistogram.getProbabilities();
        assertNotNull("probabilities", probabilities);
        assertEquals(LENGTH, probabilities.size());
        assertEquals(PROBABILITY, probabilities.get(0)
            .floatValue(), 0);

        assertEquals(FINAL_SKIP_COUNT, bootstrapHistogram.getFinalSkipCount());
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullStatistics() {
        new DvBootstrapHistogram(null, null, FINAL_SKIP_COUNT);
    }

    @Test(expected = NullPointerException.class)
    public void testBadConstructorNullProbabilities() {
        new DvBootstrapHistogram(new ArrayList<Float>(), null, FINAL_SKIP_COUNT);
    }

    @Test
    public void testEquals() {
        // Include all don't-care fields here.
        DvBootstrapHistogram bh = createBootstrapHistogram(STATISTIC,
            PROBABILITY, LENGTH, FINAL_SKIP_COUNT);
        assertEquals(bootstrapHistogram, bh);

        bh = createBootstrapHistogram(STATISTIC + 1, PROBABILITY, LENGTH,
            FINAL_SKIP_COUNT);
        assertFalse("equals", bootstrapHistogram.equals(bh));

        bh = createBootstrapHistogram(STATISTIC, PROBABILITY + 1, LENGTH,
            FINAL_SKIP_COUNT);
        assertFalse("equals", bootstrapHistogram.equals(bh));

        bh = createBootstrapHistogram(STATISTIC, PROBABILITY, LENGTH + 1,
            FINAL_SKIP_COUNT);
        assertFalse("equals", bootstrapHistogram.equals(bh));

        bh = createBootstrapHistogram(STATISTIC, PROBABILITY, LENGTH,
            FINAL_SKIP_COUNT + 1);
        assertFalse("equals", bootstrapHistogram.equals(bh));
    }

    @Test
    public void testHashCode() {
        // Include all don't-care fields here.
        DvBootstrapHistogram bh = createBootstrapHistogram(STATISTIC,
            PROBABILITY, LENGTH, FINAL_SKIP_COUNT);
        assertEquals(bootstrapHistogram.hashCode(), bh.hashCode());

        bh = createBootstrapHistogram(STATISTIC + 1, PROBABILITY, LENGTH,
            FINAL_SKIP_COUNT);
        assertFalse("hashCode", bootstrapHistogram.hashCode() == bh.hashCode());

        bh = createBootstrapHistogram(STATISTIC, PROBABILITY + 1, LENGTH,
            FINAL_SKIP_COUNT);
        assertFalse("hashCode", bootstrapHistogram.hashCode() == bh.hashCode());

        bh = createBootstrapHistogram(STATISTIC, PROBABILITY, LENGTH + 1,
            FINAL_SKIP_COUNT);
        assertFalse("hashCode", bootstrapHistogram.hashCode() == bh.hashCode());

        bh = createBootstrapHistogram(STATISTIC, PROBABILITY, LENGTH,
            FINAL_SKIP_COUNT + 1);
        assertFalse("hashCode", bootstrapHistogram.hashCode() == bh.hashCode());
    }

    @Test
    public void testToString() {
        // Check log and ensure that output isn't brutally long.
        log.info(bootstrapHistogram.toString());
    }
}
