package com.hubspot.singularity.scheduler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.hubspot.singularity.ExtendedTaskState;
import com.hubspot.singularity.SingularityTaskCleanup;
import com.hubspot.singularity.SingularityTaskHistoryUpdate;
import com.hubspot.singularity.SlavePlacement;
import com.hubspot.singularity.TaskCleanupType;

public class SingularitySlavePlacementTest extends SingularitySchedulerTestBase {

  public SingularitySlavePlacementTest() {
    super(false);
  }

  @Test
  public void testSlavePlacementSeparate() {
    initRequest();
    initFirstDeploy();

    saveAndSchedule(request.toBuilder().setInstances(Optional.of(2)).setSlavePlacement(Optional.of(SlavePlacement.SEPARATE)));

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave1", "host1"), createOffer(20, 20000, "slave1", "host1")));

    Assert.assertTrue(taskManager.getPendingTaskIds().size() == 1);
    Assert.assertTrue(taskManager.getActiveTaskIds().size() == 1);

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave1", "host1")));

    Assert.assertTrue(taskManager.getPendingTaskIds().size() == 1);
    Assert.assertTrue(taskManager.getActiveTaskIds().size() == 1);

    eventListener.taskHistoryUpdateEvent(new SingularityTaskHistoryUpdate(taskManager.getActiveTaskIds().get(0), System.currentTimeMillis(), ExtendedTaskState.TASK_CLEANING, Optional.<String>absent(), Optional.<String>absent()));

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave1", "host1")));

    Assert.assertTrue(taskManager.getPendingTaskIds().size() == 1);
    Assert.assertTrue(taskManager.getActiveTaskIds().size() == 1);

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave2", "host2")));

    Assert.assertTrue(taskManager.getPendingTaskIds().isEmpty());
    Assert.assertTrue(taskManager.getActiveTaskIds().size() == 2);
  }

  @Test
  public void testSlavePlacementOptimistic() {
    initRequest();
    initFirstDeploy();

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave1", "host1"), createOffer(20, 20000, "slave2", "host2")));

    saveAndSchedule(request.toBuilder().setInstances(Optional.of(3)).setSlavePlacement(Optional.of(SlavePlacement.OPTIMISTIC)));

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave1", "host1")));

    Assert.assertTrue(taskManager.getActiveTaskIds().size() < 3);

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave1", "host1")));

    Assert.assertTrue(taskManager.getActiveTaskIds().size() < 3);

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave2", "host2")));

    eventListener.taskHistoryUpdateEvent(new SingularityTaskHistoryUpdate(taskManager.getActiveTaskIds().get(0), System.currentTimeMillis(), ExtendedTaskState.TASK_CLEANING, Optional.<String>absent(), Optional.<String>absent()));

    Assert.assertTrue(taskManager.getPendingTaskIds().isEmpty());
    Assert.assertTrue(taskManager.getActiveTaskIds().size() == 3);
  }

  @Test
  public void testSlavePlacementOptimisticSingleOffer() {
    initRequest();
    initFirstDeploy();

    saveAndSchedule(request.toBuilder().setInstances(Optional.of(3)).setSlavePlacement(Optional.of(SlavePlacement.OPTIMISTIC)));

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave1", "host1"), createOffer(20, 20000, "slave2", "host2")));

    eventListener.taskHistoryUpdateEvent(new SingularityTaskHistoryUpdate(taskManager.getActiveTaskIds().get(0), System.currentTimeMillis(), ExtendedTaskState.TASK_CLEANING, Optional.<String>absent(), Optional.<String>absent()));

    Assert.assertTrue(taskManager.getPendingTaskIds().isEmpty());
    Assert.assertTrue(taskManager.getActiveTaskIds().size() == 3);
  }

  @Test
  public void testSlavePlacementGreedy() {
    initRequest();
    initFirstDeploy();

    saveAndSchedule(request.toBuilder().setInstances(Optional.of(3)).setSlavePlacement(Optional.of(SlavePlacement.GREEDY)));

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave1", "host1")));

    Assert.assertTrue(taskManager.getActiveTaskIds().size() == 3);
  }

  @Test
  public void testReservedSlaveAttribute() {
    Map<String, List<String>> reservedAttributes = new HashMap<>();
    reservedAttributes.put("reservedKey", Arrays.asList("reservedValue1"));
    configuration.setReserveSlavesWithAttributes(reservedAttributes);

    initRequest();
    initFirstDeploy();
    saveAndSchedule(request.toBuilder().setInstances(Optional.of(1)));

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave1", "host1", Optional.<String>absent(), ImmutableMap.of("reservedKey", "reservedValue1"))));

    Assert.assertTrue(taskManager.getActiveTaskIds().size() == 0);

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave2", "host2", Optional.<String>absent(), ImmutableMap.of("reservedKey", "notAReservedValue"))));

    Assert.assertTrue(taskManager.getActiveTaskIds().size() == 1);
  }

  @Test
  public void testReservedSlaveWithMatchinRequestAttribute() {
    Map<String, List<String>> reservedAttributes = new HashMap<>();
    reservedAttributes.put("reservedKey", Arrays.asList("reservedValue1"));
    configuration.setReserveSlavesWithAttributes(reservedAttributes);

    Map<String, String> reservedAttributesMap = ImmutableMap.of("reservedKey", "reservedValue1");
    initRequest();
    initFirstDeploy();
    saveAndSchedule(request.toBuilder().setInstances(Optional.of(1)));

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave1", "host1", Optional.<String>absent(), reservedAttributesMap)));

    Assert.assertTrue(taskManager.getActiveTaskIds().size() == 0);

    saveAndSchedule(request.toBuilder().setInstances(Optional.of(1)).setRequiredSlaveAttributes(Optional.of(reservedAttributesMap)));

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave1", "host1", Optional.<String>absent(), ImmutableMap.of("reservedKey", "reservedValue1"))));

    Assert.assertTrue(taskManager.getActiveTaskIds().size() == 1);
  }

  @Test
  public void testAllowedSlaveAttributes() {
    Map<String, List<String>> reservedAttributes = new HashMap<>();
    reservedAttributes.put("reservedKey", Arrays.asList("reservedValue1"));
    configuration.setReserveSlavesWithAttributes(reservedAttributes);

    Map<String, String> allowedAttributes = new HashMap<>();
    allowedAttributes.put("reservedKey", "reservedValue1");

    initRequest();
    initFirstDeploy();
    saveAndSchedule(request.toBuilder().setInstances(Optional.of(1)));

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave1", "host1", Optional.<String>absent(), ImmutableMap.of("reservedKey", "reservedValue1"))));

    Assert.assertTrue(taskManager.getActiveTaskIds().size() == 0);

    saveAndSchedule(request.toBuilder().setInstances(Optional.of(1)).setAllowedSlaveAttributes(Optional.of(allowedAttributes)));

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave1", "host1", Optional.<String>absent(), ImmutableMap.of("reservedKey", "reservedValue1"))));

    Assert.assertTrue(taskManager.getActiveTaskIds().size() == 1);
  }

  @Test
  public void testRequiredSlaveAttributesForRequest() {
    Map<String, String> requiredAttributes = new HashMap<>();
    requiredAttributes.put("requiredKey", "requiredValue1");

    initRequest();
    initFirstDeploy();
    saveAndSchedule(request.toBuilder().setInstances(Optional.of(1)).setRequiredSlaveAttributes(Optional.of(requiredAttributes)));

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave1", "host1", Optional.<String>absent(), ImmutableMap.of("requiredKey", "notTheRightValue"))));
    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave2", "host2", Optional.<String>absent(), ImmutableMap.of("notTheRightKey", "requiredValue1"))));

    Assert.assertTrue(taskManager.getActiveTaskIds().size() == 0);

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave2", "host2", Optional.<String>absent(), requiredAttributes)));

    Assert.assertTrue(taskManager.getActiveTaskIds().size() == 1);
  }

  @Test
  public void testMultipleRequiredAttributes() {
    Map<String, String> requiredAttributes = new HashMap<>();
    requiredAttributes.put("requiredKey1", "requiredValue1");
    requiredAttributes.put("requiredKey2", "requiredValue2");

    initRequest();
    initFirstDeploy();
    saveAndSchedule(request.toBuilder().setInstances(Optional.of(1)).setRequiredSlaveAttributes(Optional.of(requiredAttributes)));

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave1", "host1", Optional.<String>absent(), ImmutableMap.of("requiredKey1", "requiredValue1"))));
    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave2", "host2", Optional.<String>absent(), ImmutableMap.of("requiredKey1", "requiredValue1", "someotherkey", "someothervalue"))));

    Assert.assertTrue(taskManager.getActiveTaskIds().size() == 0);

    sms.resourceOffers(driver, Arrays.asList(createOffer(20, 20000, "slave2", "host2", Optional.<String>absent(), requiredAttributes)));

    Assert.assertTrue(taskManager.getActiveTaskIds().size() == 1);
  }

  @Test
  public void testEvenRackPlacement() {
    // Set up 3 active racks
    sms.resourceOffers(driver, Arrays.asList(createOffer(1, 128, "slave1", "host1", Optional.of("rack1"))));
    sms.resourceOffers(driver, Arrays.asList(createOffer(1, 128, "slave2", "host2", Optional.of("rack2"))));
    sms.resourceOffers(driver, Arrays.asList(createOffer(1, 128, "slave3", "host3", Optional.of("rack3"))));

    initRequest();
    initFirstDeploy();
    saveAndSchedule(request.toBuilder().setInstances(Optional.of(7)).setRackSensitive(Optional.of(true)));

    // rack1 -> 1, rack2 -> 2, rack3 -> 3
    sms.resourceOffers(driver, Arrays.asList(createOffer(1, 128, "slave1", "host1", Optional.of("rack1"))));
    sms.resourceOffers(driver, Arrays.asList(createOffer(1, 128, "slave2", "host2", Optional.of("rack2"))));
    sms.resourceOffers(driver, Arrays.asList(createOffer(1, 128, "slave3", "host3", Optional.of("rack3"))));

    Assert.assertEquals(3, taskManager.getActiveTaskIds().size());

    sms.resourceOffers(driver, Arrays.asList(createOffer(1, 128, "slave1", "host1", Optional.of("rack1"))));
    Assert.assertEquals(4, taskManager.getActiveTaskIds().size());

    sms.resourceOffers(driver, Arrays.asList(createOffer(1, 128, "slave1", "host1", Optional.of("rack1"))));
    Assert.assertEquals(4, taskManager.getActiveTaskIds().size());

    sms.resourceOffers(driver, Arrays.asList(createOffer(1, 128, "slave2", "host2", Optional.of("rack2"))));
    Assert.assertEquals(5, taskManager.getActiveTaskIds().size());

    // rack1 should not get a third instance until rack3 has a second
    sms.resourceOffers(driver, Arrays.asList(createOffer(1, 128, "slave1", "host1", Optional.of("rack1"))));
    Assert.assertEquals(5, taskManager.getActiveTaskIds().size());

    sms.resourceOffers(driver, Arrays.asList(createOffer(1, 128, "slave3", "host3", Optional.of("rack3"))));
    Assert.assertEquals(6, taskManager.getActiveTaskIds().size());

    sms.resourceOffers(driver, Arrays.asList(createOffer(1, 128, "slave1", "host1", Optional.of("rack1"))));
    Assert.assertEquals(7, taskManager.getActiveTaskIds().size());
  }

  @Test
  public void testRackPlacementOnScaleDown() {
    try {
      configuration.setRebalanceRacksOnScaleDown(true);
      // Set up 3 active racks
      sms.resourceOffers(driver, Arrays.asList(createOffer(1, 128, "slave1", "host1", Optional.of("rack1"))));
      sms.resourceOffers(driver, Arrays.asList(createOffer(1, 128, "slave2", "host2", Optional.of("rack2"))));
      sms.resourceOffers(driver, Arrays.asList(createOffer(1, 128, "slave3", "host3", Optional.of("rack3"))));

      initRequest();
      initFirstDeploy();
      saveAndSchedule(request.toBuilder().setInstances(Optional.of(7)).setRackSensitive(Optional.of(true)));

      sms.resourceOffers(driver, Arrays.asList(createOffer(2, 256, "slave1", "host1", Optional.of("rack1"))));
      sms.resourceOffers(driver, Arrays.asList(createOffer(2, 256, "slave2", "host2", Optional.of("rack2"))));
      sms.resourceOffers(driver, Arrays.asList(createOffer(3, 384, "slave3", "host3", Optional.of("rack3"))));

      Assert.assertEquals(7, taskManager.getActiveTaskIds().size());

      requestResource.postRequest(request.toBuilder().setInstances(Optional.of(4)).setRackSensitive(Optional.of(true)).build());

      scheduler.drainPendingQueue(stateCacheProvider.get());

      Assert.assertEquals(4, taskManager.getNumCleanupTasks());

      int rebalanceRackCleanups = 0;
      for (SingularityTaskCleanup cleanup : taskManager.getCleanupTasks()) {
        if (cleanup.getCleanupType() == TaskCleanupType.REBALANCE_RACKS) {
          rebalanceRackCleanups++;
        }
      }
      Assert.assertEquals(1, rebalanceRackCleanups);
      Assert.assertEquals(1, taskManager.getPendingTaskIds().size());
    } finally {
      configuration.setRebalanceRacksOnScaleDown(false);
    }
  }
}
