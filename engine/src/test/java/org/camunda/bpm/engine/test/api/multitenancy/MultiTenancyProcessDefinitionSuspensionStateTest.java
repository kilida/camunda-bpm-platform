/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camunda.bpm.engine.test.api.multitenancy;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.Calendar;
import java.util.Date;

import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.camunda.bpm.engine.impl.jobexecutor.TimerActivateProcessDefinitionHandler;
import org.camunda.bpm.engine.impl.jobexecutor.TimerSuspendProcessDefinitionHandler;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.repository.ProcessDefinitionQuery;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

public class MultiTenancyProcessDefinitionSuspensionStateTest extends PluggableProcessEngineTestCase {

  protected static final String TENANT_ONE = "tenant1";
  protected static final String TENANT_TWO = "tenant2";

  protected static final String PROCESS_DEFINITION_KEY = "testProcess";

  protected static final BpmnModelInstance PROCESS = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .startEvent()
      .userTask()
      .endEvent()
      .done();

  @Override
  protected void setUp() throws Exception {

    deploymentForTenant(TENANT_ONE, PROCESS);
    deploymentForTenant(TENANT_TWO, PROCESS);
    deployment(PROCESS);

    runtimeService.createProcessInstanceByKey(PROCESS_DEFINITION_KEY).processDefinitionTenantId(TENANT_ONE).execute();
    runtimeService.createProcessInstanceByKey(PROCESS_DEFINITION_KEY).processDefinitionTenantId(TENANT_TWO).execute();
    runtimeService.createProcessInstanceByKey(PROCESS_DEFINITION_KEY).processDefinitionWithoutTenantId().execute();
  }

  public void testSuspendAndActivateProcessDefinitionsForAllTenants() {
    // given activated process definitions
    ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    // first suspend
    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .suspend();

    assertThat(query.active().count(), is(0L));
    assertThat(query.suspended().count(), is(3L));

    // then activate
    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .activate();

    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));
  }

  public void testSuspendProcessDefinitionForTenant() {
    // given activated process definitions
    ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .processDefinitionTenantId(TENANT_ONE)
      .suspend();

    assertThat(query.active().count(), is(2L));
    assertThat(query.suspended().count(), is(1L));
    assertThat(query.suspended().tenantIdIn(TENANT_ONE).count(), is(1L));
  }

  public void testSuspendProcessDefinitionForNonTenant() {
    // given activated process definitions
    ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .processDefinitionWithoutTenantId()
      .suspend();

    assertThat(query.active().count(), is(2L));
    assertThat(query.suspended().count(), is(1L));
    assertThat(query.suspended().withoutTenantId().count(), is(1L));
  }

  public void testActivateProcessDefinitionForTenant() {
    // given suspend process definitions
    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .suspend();

    ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery();
    assertThat(query.suspended().count(), is(3L));
    assertThat(query.active().count(), is(0L));

    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .processDefinitionTenantId(TENANT_ONE)
      .activate();

    assertThat(query.suspended().count(), is(2L));
    assertThat(query.active().count(), is(1L));
    assertThat(query.active().tenantIdIn(TENANT_ONE).count(), is(1L));
  }

  public void testActivateProcessDefinitionForNonTenant() {
    // given suspend process definitions
    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .suspend();

    ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery();
    assertThat(query.suspended().count(), is(3L));
    assertThat(query.active().count(), is(0L));

    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .processDefinitionWithoutTenantId()
      .activate();

    assertThat(query.suspended().count(), is(2L));
    assertThat(query.active().count(), is(1L));
    assertThat(query.active().withoutTenantId().count(), is(1L));
  }

  public void testSuspendAndActivateProcessDefinitionsIncludeInstancesForAllTenants() {
    // given activated process instances
    ProcessInstanceQuery query = runtimeService.createProcessInstanceQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    // first suspend
    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .includeProcessInstances(true)
      .suspend();

    assertThat(query.active().count(), is(0L));
    assertThat(query.suspended().count(), is(3L));

    // then activate
    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .includeProcessInstances(true)
      .activate();

    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));
  }

  public void testSuspendProcessDefinitionIncludeInstancesForTenant() {
    // given activated process instances
    ProcessInstanceQuery query = runtimeService.createProcessInstanceQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .processDefinitionTenantId(TENANT_ONE)
      .includeProcessInstances(true)
      .suspend();

    assertThat(query.active().count(), is(2L));
    assertThat(query.suspended().count(), is(1L));
    assertThat(query.suspended().tenantIdIn(TENANT_ONE).count(), is(1L));
  }

  public void testSuspendProcessDefinitionIncludeInstancesForNonTenant() {
    // given activated process instances
    ProcessInstanceQuery query = runtimeService.createProcessInstanceQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .processDefinitionWithoutTenantId()
      .includeProcessInstances(true)
      .suspend();

    assertThat(query.active().count(), is(2L));
    assertThat(query.suspended().count(), is(1L));
    assertThat(query.suspended().withoutTenantId().count(), is(1L));
  }

  public void testActivateProcessDefinitionIncludeInstancesForTenant() {
    // given suspended process instances
    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .includeProcessInstances(true)
      .suspend();

    ProcessInstanceQuery query = runtimeService.createProcessInstanceQuery();
    assertThat(query.suspended().count(), is(3L));
    assertThat(query.active().count(), is(0L));

    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .processDefinitionTenantId(TENANT_ONE)
      .includeProcessInstances(true)
      .activate();

    assertThat(query.suspended().count(), is(2L));
    assertThat(query.active().count(), is(1L));
    assertThat(query.active().tenantIdIn(TENANT_ONE).count(), is(1L));
  }

  public void testActivateProcessDefinitionIncludeInstancesForNonTenant() {
    // given suspended process instances
    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .includeProcessInstances(true)
      .suspend();

    ProcessInstanceQuery query = runtimeService.createProcessInstanceQuery();
    assertThat(query.suspended().count(), is(3L));
    assertThat(query.active().count(), is(0L));

    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .processDefinitionWithoutTenantId()
      .includeProcessInstances(true)
      .activate();

    assertThat(query.suspended().count(), is(2L));
    assertThat(query.active().count(), is(1L));
    assertThat(query.active().withoutTenantId().count(), is(1L));
  }

  public void testDelayedSuspendProcessDefinitionsForAllTenants() {
    // given activated process definitions

    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .executionDate(tomorrow())
      .suspend();

    ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    // when execute the job to suspend the process definitions
    Job job = managementService.createJobQuery().singleResult();
    assertThat(job, is(notNullValue()));

    managementService.executeJob(job.getId());

    assertThat(query.active().count(), is(0L));
    assertThat(query.suspended().count(), is(3L));
  }

  public void testDelayedSuspendProcessDefinitionsForTenant() {
    // given activated process definitions

    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .processDefinitionTenantId(TENANT_ONE)
      .executionDate(tomorrow())
      .suspend();

    ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    // when execute the job to suspend the process definition
    Job job = managementService.createJobQuery().singleResult();
    assertThat(job, is(notNullValue()));

    managementService.executeJob(job.getId());

    assertThat(query.active().count(), is(2L));
    assertThat(query.suspended().count(), is(1L));
    assertThat(query.suspended().tenantIdIn(TENANT_ONE).count(), is(1L));
  }

  public void testDelayedSuspendProcessDefinitionsForNonTenant() {
    // given activated process definitions

    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .processDefinitionWithoutTenantId()
      .executionDate(tomorrow())
      .suspend();

    ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery();
    assertThat(query.active().count(), is(3L));
    assertThat(query.suspended().count(), is(0L));

    // when execute the job to suspend the process definition
    Job job = managementService.createJobQuery().singleResult();
    assertThat(job, is(notNullValue()));

    managementService.executeJob(job.getId());

    assertThat(query.active().count(), is(2L));
    assertThat(query.suspended().count(), is(1L));
    assertThat(query.suspended().withoutTenantId().count(), is(1L));
  }

  public void testDelayedActivateProcessDefinitionsForAllTenants() {
    // given suspended process definitions
    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .suspend();

    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .executionDate(tomorrow())
      .activate();

    ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery();
    assertThat(query.suspended().count(), is(3L));
    assertThat(query.active().count(), is(0L));

    // when execute the job to activate the process definitions
    Job job = managementService.createJobQuery().singleResult();
    assertThat(job, is(notNullValue()));

    managementService.executeJob(job.getId());

    assertThat(query.suspended().count(), is(0L));
    assertThat(query.active().count(), is(3L));
  }

  public void testDelayedActivateProcessDefinitionsForTenant() {
    // given suspended process definitions
    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .suspend();

    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .processDefinitionTenantId(TENANT_ONE)
      .executionDate(tomorrow())
      .activate();

    ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery();
    assertThat(query.suspended().count(), is(3L));
    assertThat(query.active().count(), is(0L));

    // when execute the job to activate the process definition
    Job job = managementService.createJobQuery().singleResult();
    assertThat(job, is(notNullValue()));

    managementService.executeJob(job.getId());

    assertThat(query.suspended().count(), is(2L));
    assertThat(query.active().count(), is(1L));
    assertThat(query.active().tenantIdIn(TENANT_ONE).count(), is(1L));
  }

  public void testDelayedActivateProcessDefinitionsForNonTenant() {
    // given suspended process definitions
    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .suspend();

    repositoryService.updateProcessDefinitionSuspensionStateByKey(PROCESS_DEFINITION_KEY)
      .processDefinitionWithoutTenantId()
      .executionDate(tomorrow())
      .activate();

    ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery();
    assertThat(query.suspended().count(), is(3L));
    assertThat(query.active().count(), is(0L));

    // when execute the job to activate the process definition
    Job job = managementService.createJobQuery().singleResult();
    assertThat(job, is(notNullValue()));

    managementService.executeJob(job.getId());

    assertThat(query.suspended().count(), is(2L));
    assertThat(query.active().count(), is(1L));
    assertThat(query.active().withoutTenantId().count(), is(1L));
  }

  public void testFailToSuspendProcessDefinitionByIdForTenant() {
    ProcessDefinition processDefinition = repositoryService
        .createProcessDefinitionQuery()
        .tenantIdIn(TENANT_ONE)
        .singleResult();

    try {
      repositoryService.updateProcessDefinitionSuspensionStateById(processDefinition.getId())
        .processDefinitionTenantId(TENANT_ONE)
        .suspend();

      fail("expected exception");
    } catch(BadUserRequestException e) {
      assertThat(e.getMessage(), containsString("Cannot specify a tenant-id"));
    }
  }

  public void testFailToSuspendProcessDefinitionByIdForNonTenant() {
    ProcessDefinition processDefinition = repositoryService
        .createProcessDefinitionQuery()
        .withoutTenantId()
        .singleResult();

    try {
      repositoryService.updateProcessDefinitionSuspensionStateById(processDefinition.getId())
        .processDefinitionWithoutTenantId()
        .suspend();

      fail("expected exception");
    } catch(BadUserRequestException e) {
      assertThat(e.getMessage(), containsString("Cannot specify a tenant-id"));
    }
  }

  public void testFailToActivateProcessDefinitionByIdForTenant() {
    ProcessDefinition processDefinition = repositoryService
        .createProcessDefinitionQuery()
        .tenantIdIn(TENANT_ONE)
        .singleResult();

    try {
      repositoryService.updateProcessDefinitionSuspensionStateById(processDefinition.getId())
        .processDefinitionTenantId(TENANT_ONE)
        .activate();

      fail("expected exception");
    } catch(BadUserRequestException e) {
      assertThat(e.getMessage(), containsString("Cannot specify a tenant-id"));
    }
  }

  public void testFailToActivateProcessDefinitionByIdForNonTenant() {
    ProcessDefinition processDefinition = repositoryService
        .createProcessDefinitionQuery()
        .withoutTenantId()
        .singleResult();

    try {
      repositoryService.updateProcessDefinitionSuspensionStateById(processDefinition.getId())
        .processDefinitionWithoutTenantId()
        .activate();

      fail("expected exception");
    } catch(BadUserRequestException e) {
      assertThat(e.getMessage(), containsString("Cannot specify a tenant-id"));
    }
  }

  protected Date tomorrow() {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.DAY_OF_YEAR, 1);
    return calendar.getTime();
  }

  @Override
  public void tearDown() throws Exception {
    CommandExecutor commandExecutor = processEngineConfiguration.getCommandExecutorTxRequired();
    commandExecutor.execute(new Command<Object>() {
      public Object execute(CommandContext commandContext) {
        commandContext.getHistoricJobLogManager().deleteHistoricJobLogsByHandlerType(TimerActivateProcessDefinitionHandler.TYPE);
        commandContext.getHistoricJobLogManager().deleteHistoricJobLogsByHandlerType(TimerSuspendProcessDefinitionHandler.TYPE);
        return null;
      }
    });
  }

}
