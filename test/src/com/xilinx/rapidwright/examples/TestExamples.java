/*
 * Copyright (c) 2023, Advanced Micro Devices, Inc.
 * All rights reserved.
 *
 * Author: Eddie Hung, Advanced Micro Devices, Inc.
 *
 * This file is part of RapidWright.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.xilinx.rapidwright.examples;

import java.nio.file.Path;

import com.xilinx.rapidwright.design.ConstraintGroup;
import com.xilinx.rapidwright.design.NetType;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.edif.EDIFCell;
import com.xilinx.rapidwright.edif.EDIFCellInst;
import com.xilinx.rapidwright.edif.EDIFNet;
import com.xilinx.rapidwright.edif.EDIFPortInst;
import com.xilinx.rapidwright.edif.EDIFTools;
import com.xilinx.rapidwright.rwroute.RWRoute;
import org.capnproto.PrimitiveList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.xilinx.rapidwright.design.Cell;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.design.PinType;
import com.xilinx.rapidwright.design.Unisim;
import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.router.Router;

public class TestExamples {
    @Test
    public void testPipelineGenerator() {
        PipelineGenerator.main(new String[]{
                "-o", "/dev/null"
        });
    }

    @Test
    public void testPipelineGeneratorWithRouting() {
        PipelineGeneratorWithRouting.main(new String[]{
                "-o", "/dev/null"
        });
    }

    /*
     * This test is a reproduction of Lesson1.java. The code is not used directly so
     * as to keep the original example simple and unencumbered.
     */
    @ParameterizedTest
    @CsvSource({
            "xczu3eg-sbva484-1-i, SLICE_X1Y1, 32, 0, 1, false",
            "xcku040-ffva1156-2-e,  SLICE_X1Y1, 32, 0, 1, false", // Device.KCU105
            "xczu3eg-sbva484-1-i, SLICE_X0Y0, 32, 0, 1, false",
            "xczu3eg-sbva484-1-i, SLICE_X0Y147, 32, 0, 1, false",
            "xczu3eg-sbva484-1-i, SLICE_X1Y1, 1, 0, 1, false",
            "xczu3eg-sbva484-1-i, SLICE_X1Y1, 65, 0, 1, false",
            "xczu3eg-sbva484-1-i, SLICE_X1Y1, 32, 1024, 1, true",
            "xczu3eg-sbva484-1-i, SLICE_X1Y1, 32, 0, 1024, false",

    })
    public void testCounterGenerator(String device,  String sliceName, int width, long initValue, long step,
                                     boolean countDown,  @TempDir Path tempDir) {
        Design d = new Design("test", device);
        Site slice = d.getDevice().getSite(sliceName);
        CounterGenerator.createCounter(d, slice, width, initValue, step, countDown);

        String submoduleName = countDown ? "subtractor" : "adder";
        EDIFCellInst submoduleInst = d.getTopEDIFCell().getCellInst(submoduleName);
        EDIFCell submodule = submoduleInst.getCellType();
        int numOfAddLuts = 0;
        int numOfSumFFs = 0;
        for (EDIFCellInst eci : submodule.getCellInsts()) {
            if (eci.getName().startsWith("add")) {
                numOfAddLuts++;
                String initString = eci.getProperty("INIT").getValue();
                Assertions.assertTrue(countDown ? initString.endsWith("h9") : initString.endsWith("h6"));
            }
            else if (eci.getName().startsWith("sum")) {
                numOfSumFFs++;
                int idx = Integer.parseInt(eci.getName().substring(3));
                int bit = (int) ((initValue >> idx) & 1);
                int init = Integer.parseInt(eci.getProperty("INIT").getValue().substring(3));
                Assertions.assertEquals(bit, init);
            }
        }

        Assertions.assertEquals(width, numOfAddLuts);
        Assertions.assertEquals(width, numOfSumFFs);

        String stepBin = new StringBuilder(Long.toBinaryString(step)).reverse().toString();

        EDIFNet gnd = EDIFTools.getStaticNet(NetType.GND, d.getTopEDIFCell(), d.getNetlist());
        EDIFNet vcc = EDIFTools.getStaticNet(NetType.VCC, d.getTopEDIFCell(), d.getNetlist());

        for(int i = 0; i < stepBin.length(); i++) {
            char bit = stepBin.charAt(i);
            EDIFNet constNet = bit == '0' ? gnd : vcc;
            EDIFPortInst pi = submoduleInst.getPortInst("B["+i+"]");
            EDIFNet net = pi.getNet();
            Assertions.assertEquals(pi.getNet(), constNet);
        }

        String countDownOpt = countDown ? "-m" : "";
        CounterGenerator.main(new String[]{"-p", device, "-o", tempDir.toAbsolutePath().toString()+"/test.dcp", "-w",
                Integer.toString(width), "-s", sliceName, "-t", Long.toString(step), "-i", Long.toString(initValue),
                countDownOpt});
    }
}
