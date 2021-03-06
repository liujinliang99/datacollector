/**
 * Copyright 2015 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.datacollector.runner;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.streamsets.datacollector.main.RuntimeInfo;
import com.streamsets.datacollector.runner.BatchImpl;
import com.streamsets.datacollector.runner.BatchMakerImpl;
import com.streamsets.datacollector.runner.ErrorSink;
import com.streamsets.datacollector.runner.FullPipeBatch;
import com.streamsets.datacollector.runner.Pipe;
import com.streamsets.datacollector.runner.PipeBatch;
import com.streamsets.datacollector.runner.PipeContext;
import com.streamsets.datacollector.runner.Pipeline;
import com.streamsets.datacollector.runner.PipelineRunner;
import com.streamsets.datacollector.runner.StagePipe;
import com.streamsets.datacollector.util.Configuration;
import com.streamsets.pipeline.api.Batch;
import com.streamsets.pipeline.api.BatchMaker;
import com.streamsets.pipeline.api.Processor;
import com.streamsets.pipeline.api.Source;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.Target;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

public class TestStagePipe {
  private boolean produce;
  private boolean process;
  private boolean write;

  @Before
  public void setUp() {
    MockStages.resetStageCaptures();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSourceStage() throws Exception {
    produce = false;
    MockStages.setSourceCapture(new Source() {
      @Override
      public String produce(String lastSourceOffset, int maxBatchSize, BatchMaker batchMaker) throws StageException {
        produce = true;
        Assert.assertEquals("offset1", lastSourceOffset);
        Assert.assertEquals(ImmutableList.of("s"), batchMaker.getLanes());
        return "offset2";
      }

      @Override
      public List<ConfigIssue> init(Info info, Context context) {
        return Collections.emptyList();
      }

      @Override
      public void destroy() {

      }
    });
    PipelineRunner pipelineRunner = Mockito.mock(PipelineRunner.class);
    Mockito.when(pipelineRunner.getMetrics()).thenReturn(new MetricRegistry());
    Mockito.when(pipelineRunner.getRuntimeInfo()).thenReturn(Mockito.mock(RuntimeInfo.class));
    Pipeline pipeline = new Pipeline.Builder(MockStages.createStageLibrary(), new Configuration(), "name", "name", "0",
                                             MockStages.createPipelineConfigurationSourceProcessorTarget())
        .build(pipelineRunner);
    StagePipe pipe = (StagePipe) pipeline.getPipes()[0];
    BatchMakerImpl batchMaker = Mockito.mock(BatchMakerImpl.class);
    Mockito.when(batchMaker.getLanes()).thenReturn(ImmutableList.of("s"));

    PipeBatch pipeBatch = Mockito.mock(FullPipeBatch.class);
    Mockito.when(pipeBatch.getPreviousOffset()).thenReturn("offset1");

    BatchImpl batch = Mockito.mock(BatchImpl.class);
    Mockito.when(batch.getSize()).thenReturn(1);
    Mockito.when(pipeBatch.getBatch(Mockito.eq(pipe))).thenReturn(batch);
    Mockito.when(pipeBatch.getErrorSink()).thenReturn(new ErrorSink());

    Mockito.when(pipeBatch.startStage(Mockito.eq(pipe))).thenReturn(batchMaker);
    Assert.assertTrue(pipe.init(new PipeContext()).isEmpty());
    pipe.process(pipeBatch);
    pipe.destroy(pipeBatch);
    Mockito.verify(pipeBatch, Mockito.times(1)).startStage(Mockito.eq(pipe));
    Mockito.verify(pipeBatch, Mockito.times(1)).getBatchSize();
    Mockito.verify(pipeBatch, Mockito.times(1)).getBatch(Mockito.any(Pipe.class));
    Mockito.verify(pipeBatch, Mockito.times(1)).getPreviousOffset();
    Mockito.verify(pipeBatch, Mockito.times(1)).setNewOffset(Mockito.eq("offset2"));
    Mockito.verify(pipeBatch, Mockito.times(1)).completeStage(Mockito.eq(batchMaker), Mockito.any(EventSink.class));
    Mockito.verify(pipeBatch, Mockito.times(1)).completeStage(Mockito.any(StagePipe.class), Mockito.any(EventSink.class));
    Mockito.verify(pipeBatch, Mockito.times(2)).getErrorSink();
    Mockito.verifyNoMoreInteractions(pipeBatch);
    Assert.assertTrue(produce);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testProcessor() throws Exception {
    process = false;
    MockStages.setProcessorCapture(new Processor() {

      @Override
      public List<ConfigIssue> init(Info info, Context context) {
        return Collections.emptyList();
      }

      @Override
      public void process(Batch batch, BatchMaker batchMaker) throws StageException {
        process = true;
        Assert.assertEquals("offset2", batch.getSourceOffset());
        Assert.assertEquals(ImmutableList.of("p"), batchMaker.getLanes());
      }

      @Override
      public void destroy() {
      }
    });
    PipelineRunner pipelineRunner = Mockito.mock(PipelineRunner.class);
    Mockito.when(pipelineRunner.getMetrics()).thenReturn(new MetricRegistry());
    Mockito.when(pipelineRunner.getRuntimeInfo()).thenReturn(Mockito.mock(RuntimeInfo.class));
    Pipeline pipeline = new Pipeline.Builder(MockStages.createStageLibrary(), new Configuration(), "name", "name", "0",
                                             MockStages.createPipelineConfigurationSourceProcessorTarget())
        .build(pipelineRunner);
    StagePipe pipe = (StagePipe) pipeline.getPipes()[4];
    BatchMakerImpl batchMaker = Mockito.mock(BatchMakerImpl.class);
    Mockito.when(batchMaker.getLanes()).thenReturn(ImmutableList.of("p"));

    BatchImpl batch = Mockito.mock(BatchImpl.class);
    Mockito.when(batch.getSourceOffset()).thenReturn("offset2");
    Mockito.when(batch.getSize()).thenReturn(1);

    PipeBatch pipeBatch = Mockito.mock(FullPipeBatch.class);
    Mockito.when(pipeBatch.startStage(Mockito.eq(pipe))).thenReturn(batchMaker);
    Mockito.when(pipeBatch.getBatch(Mockito.eq(pipe))).thenReturn(batch);
    Mockito.when(pipeBatch.getErrorSink()).thenReturn(new ErrorSink());

    Assert.assertTrue(pipe.init(new PipeContext()).isEmpty());
    pipe.process(pipeBatch);
    pipe.destroy(pipeBatch);

    Mockito.verify(pipeBatch, Mockito.times(1)).startStage(Mockito.eq(pipe));
    Mockito.verify(pipeBatch, Mockito.times(1)).getBatch(Mockito.eq(pipe));
    Mockito.verify(pipeBatch, Mockito.times(1)).getPreviousOffset();
    Mockito.verify(pipeBatch, Mockito.times(1)).getBatchSize();
    Mockito.verify(pipeBatch, Mockito.times(1)).completeStage(Mockito.eq(batchMaker), Mockito.any(EventSink.class));
    Mockito.verify(pipeBatch, Mockito.times(1)).completeStage(Mockito.any(StagePipe.class), Mockito.any(EventSink.class));
    Mockito.verify(pipeBatch, Mockito.times(2)).getErrorSink();
    Mockito.verifyNoMoreInteractions(pipeBatch);
    Assert.assertTrue(process);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testTarget() throws Exception {
    write = false;
    MockStages.setTargetCapture(new Target() {

      @Override
      public List<ConfigIssue> init(Info info, Context context) {
        return Collections.emptyList();
      }

      @Override
      public void write(Batch batch) throws StageException {
        write = true;
        Assert.assertEquals("offset2", batch.getSourceOffset());
      }

      @Override
      public void destroy() {
      }
    });

    PipelineRunner pipelineRunner = Mockito.mock(PipelineRunner.class);
    Mockito.when(pipelineRunner.getMetrics()).thenReturn(new MetricRegistry());
    Mockito.when(pipelineRunner.getRuntimeInfo()).thenReturn(Mockito.mock(RuntimeInfo.class));
    Pipeline pipeline = new Pipeline.Builder(MockStages.createStageLibrary(), new Configuration(), "name", "name", "0",
                                             MockStages.createPipelineConfigurationSourceProcessorTarget())
        .build(pipelineRunner);
    StagePipe pipe = (StagePipe) pipeline.getPipes()[8];
    BatchMakerImpl batchMaker = Mockito.mock(BatchMakerImpl.class);
    Mockito.when(batchMaker.getLanes()).thenReturn(ImmutableList.of("t"));

    BatchImpl batch = Mockito.mock(BatchImpl.class);
    Mockito.when(batch.getSourceOffset()).thenReturn("offset2");
    Mockito.when(batch.getSize()).thenReturn(1);

    PipeBatch pipeBatch = Mockito.mock(FullPipeBatch.class);
    Mockito.when(pipeBatch.startStage(Mockito.eq(pipe))).thenReturn(batchMaker);
    Mockito.when(pipeBatch.getBatch(Mockito.eq(pipe))).thenReturn(batch);
    Mockito.when(pipeBatch.getErrorSink()).thenReturn(new ErrorSink());

    Assert.assertTrue(pipe.init(new PipeContext()).isEmpty());
    pipe.process(pipeBatch);
    pipe.destroy(pipeBatch);

    Mockito.verify(pipeBatch, Mockito.times(1)).startStage(Mockito.eq(pipe));
    Mockito.verify(pipeBatch, Mockito.times(1)).getBatch(Mockito.eq(pipe));
    Mockito.verify(pipeBatch, Mockito.times(1)).getPreviousOffset();
    Mockito.verify(pipeBatch, Mockito.times(1)).getBatchSize();
    Mockito.verify(pipeBatch, Mockito.times(1)).completeStage(Mockito.eq(batchMaker), Mockito.any(EventSink.class));
    Mockito.verify(pipeBatch, Mockito.times(1)).completeStage(Mockito.any(StagePipe.class), Mockito.any(EventSink.class));
    Mockito.verify(pipeBatch, Mockito.times(2)).getErrorSink();
    Mockito.verifyNoMoreInteractions(pipeBatch);
    Assert.assertTrue(write);
  }
}
