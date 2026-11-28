
package com.onyx.perfmonitor;

import org.junit.Test;

import static org.junit.Assert.*;

public class GpuDevfreqMatcherTest {

    @Test
    public void acceptsKnownGpuDeviceNames() {
        assertTrue(GpuDevfreqMatcher.matches("gpu"));
        assertTrue(GpuDevfreqMatcher.matches("mali"));
        assertTrue(GpuDevfreqMatcher.matches("kgsl-3d0"));
        assertTrue(GpuDevfreqMatcher.matches("pvr"));
        assertTrue(GpuDevfreqMatcher.matches("PowerVR"));
        assertTrue(GpuDevfreqMatcher.matches("gpu-devfreq"));
        assertTrue(GpuDevfreqMatcher.matches("rgx"));
        assertTrue(GpuDevfreqMatcher.matches("xclipse"));
        assertTrue(GpuDevfreqMatcher.matches("g3d"));
        assertTrue(GpuDevfreqMatcher.matches("sgpu"));
        assertTrue(GpuDevfreqMatcher.matches("panfrost"));
    }

    @Test
    public void rejectsGenericThreeDDeviceNames() {
        assertFalse(GpuDevfreqMatcher.matches("3d"));
        assertFalse(GpuDevfreqMatcher.matches("3D-core"));
        assertFalse(GpuDevfreqMatcher.matches("ufs-3d-controller"));
        assertFalse(GpuDevfreqMatcher.matches("storage-3d"));
    }

    @Test
    public void matchingIsCaseInsensitive() {
        assertTrue(GpuDevfreqMatcher.matches("MALI"));
        assertTrue(GpuDevfreqMatcher.matches("GPU-DEVFREQ"));
        assertTrue(GpuDevfreqMatcher.matches("PowerVR"));
    }

    @Test
    public void rejectsUnrelatedDeviceNames() {
        assertFalse(GpuDevfreqMatcher.matches("cpu"));
        assertFalse(GpuDevfreqMatcher.matches("cpufreq"));
        assertFalse(GpuDevfreqMatcher.matches("memlat"));
        assertFalse(GpuDevfreqMatcher.matches("system"));
        assertFalse(GpuDevfreqMatcher.matches("interconnect"));
    }

    @Test
    public void rejectsNullAndEmptyNames() {
        assertFalse(GpuDevfreqMatcher.matches(null));
        assertFalse(GpuDevfreqMatcher.matches(""));
        assertFalse(GpuDevfreqMatcher.matches("   "));
    }

    @Test
    public void generatesExpectedShellPatterns() {
        assertEquals(
                "*gpu*|*mali*|*kgsl*|*pvr*|*powervr*|*rgx*|*xclipse*|*g3d*|*sgpu*|*panfrost*",
                GpuDevfreqMatcher.shellCasePatterns()
        );
    }
}
