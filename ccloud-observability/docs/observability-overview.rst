.. _ccloud-observability-overview:

Observability Overview and Setup
--------------------------------

Using |ccloud| has the advantage of circumventing the trials and tribulations one would face when monitoring
an on-prem Kafka cluster, but you still need to monitor your client applications and, to some degree, your |ccloud| cluster.
Your success in |ccloud| largely depends on how well your applications are performing. Observability into the performance and status of your client
applications gives you insights on how to fine tune your producers and consumers, when to scale
your |ccloud| cluster, what might be going wrong, and how to resolve the problem.

This module covers how to set up a time-series database populated with data from the
|ccloud| Metrics API and client metrics from a locally running Java consumer and producer,
along with how to set up a data visualization tool. After the initial setup, you will
follow a series of scenarios to create failure scenarios and to be alerted when the errors occur.

.. note::

   This example uses Prometheus as the time-series database and Grafana for visualization, but the same principles can be applied to any other technologies.


Prerequisites
~~~~~~~~~~~~~

-  Access to `Confluent Cloud <https://confluent.cloud/login>`__.

-  Local `install of Confluent Cloud CLI
   <https://docs.confluent.io/ccloud-cli/current/install.html>`__ (v1.25.0 or later)

-  `jq <https://github.com/stedolan/jq/wiki/Installation>`__ installed on your host

-  `Docker <https://docs.docker.com/get-docker/>`__ installed on your host

Cost to Run Tutorial
~~~~~~~~~~~~~~~~~~~~

Caution
^^^^^^^

.. include:: ../../ccloud/docs/includes/ccloud-examples-caution.rst

|ccloud| Promo Code
^^^^^^^^^^^^^^^^^^^

.. include:: ../../ccloud/docs/includes/ccloud-examples-promo-code.rst

|ccloud| Cluster and Observability Container Setup
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The following instructions will:

-  use ``ccloud-stack`` to create a |ccloud| cluster, a service account with proper acls, and a client configuration file

-  create a ``cloud`` resource api-key for the ``ccloud-exporter``

-  build a |ak| client docker image with the maven project's dependencies cache

-  stand up numerous docker containers (1 consumer with JMX exporter, 1 producer with JMX exporter, Prometheus, Grafana, a ccloud-exporter, and a node-exporter) with ``docker-compose``

#. Log in to the |ccloud| CLI:

   .. code-block:: bash

      ccloud login --save

   The ``--save`` flag will save your |ccloud| login credentials to the
   ``~/.netrc`` file.


#. Clone the `confluentinc/examples <https://github.com/confluentinc/examples>`__ GitHub repository.

   .. code-block:: bash

       git clone https://github.com/confluentinc/examples.git

#. Navigate to the ``examples/ccloud-observability/`` directory and switch to
   the |cp| release branch:

   .. codewithvars:: bash

       cd examples/ccloud-observability/
       git checkout |release_post_branch|

#. Setup a |ccloud| cluster, secrets, and observability components by running :devx-examples:`start.sh script|ccloud-observability/start.sh`:

   .. code-block:: bash

      ./start.sh

#. It will take up to 3 minutes for data to become visible in Grafana.
   Open `Grafana <localhost:3000>`__ and use the username ``admin`` and password ``password`` to login.
   Now you are ready to proceed to Producer, Consumer, or General scenarios to see what different failure scenarios look like.


Validate Setup
~~~~~~~~~~~~~~

#. Validate the producer and consumer |ak| clients are running.  From the Confluent Cloud UI, view the **Data flow** in your newly created environment and |ak| cluster.

   |Data Flow|

#. Navigate to the `Prometheus Targets page <localhost:9090/targets>`__.

   |Prometheus Targets Unknown|

   This page will show you if Prometheus is scraping the targets you have created. It should look like below after 2 minutes if everything is working. You may need to refresh the page.

   |Prometheus Targets Up|

#. It will take up to 3 minutes for data to become visible in Grafana.
   Open `Grafana <localhost:3000>`__ and use the username ``admin`` and password ``password`` to login.

#. Now you are ready to proceed to Producer, Consumer, or General scenarios to see what different failure scenarios look like.


.. |Data Flow|
   image:: images/data-flow.png
   :alt: Data Flow

.. |Prometheus Targets Unknown|
   image:: images/prometheus-targets-unknown.png
   :alt: Prometheus Targets Unknown

.. |Prometheus Targets Up|
   image:: images/prometheus-targets-up.png
   :alt: Prometheus Targets Up

.. _ccloud-observability-producer-overview:

Producer Client Scenarios
~~~~~~~~~~~~~~~~~~~~~~~~~

The dashboard and scenarios in this section use client metrics from a Java producer. The same principles can be applied to any
other non-java clients--they generally offer similar metrics.

The source code for the client can found in the :devx-examples:`ccloud-observability/src|ccloud-observability/src` directory.
The sample client uses default configurations, this is not recommended for production use cases.
This Java producer will continue to produce the same message every 100 ms until the process is interrupted.
The content of the message is not important here, in these scenarios the focus is on the change in client metric values.

|Producer Dashboard|


.. |Producer Dashboard|
   image:: images/producer-dashboard.png
   :alt: Producer Dashboard

.. _ccloud-observability-producer-confluent-cloud-unreachable:

|ccloud| Unreachable
^^^^^^^^^^^^^^^^^^^^

In the ``producer`` container, add a rule blocking network traffic that has a destination TCP port ``9092``. This will prevent the producer from reaching the |ak| cluster in |ccloud|.

This scenario will look at |ccloud| metrics from the Metrics API and client metrics from the client application's MBean object ``kafka.producer:type=producer-metrics,client-id=producer-1``.

^^^^^^^^^^^^^^^^^^^^^^^^^^
Introduce failure scenario
^^^^^^^^^^^^^^^^^^^^^^^^^^

#. Add a rule blocking traffic in the ``producer`` container on port ``9092`` which is used to talk to the broker:

   .. code-block:: bash

      docker-compose exec producer iptables -A OUTPUT -p tcp --dport 9092 -j DROP

^^^^^^^^^^^^^^^^^^^^
Diagnose the problem
^^^^^^^^^^^^^^^^^^^^

#. From your web browser, navigate to the Grafana dashboard at http://localhost:3000 and login with the username ``admin`` and password ``password``.

#. Navigate to the ``Producer Client Metrics`` dashboard.  Wait 2 minutes and then observe:

   -  A downward trend in outgoing bytes which can be found by the expanding the ``Throughput`` tab.

   -  The top level panels like ``Record error rate`` (derived from |ak| MBean attribute ``record-error-rate``) should turn red, a major indication something is wrong.

   -  The spark line in the ``Free buffer space`` (derived from |ak| MBean attribute ``buffer-available-bytes``) panel go down and a bump in ``Retry rate`` (derived from |ak| MBean attribute ``record-retry-rate``)

   This means the producer is not producing data, which could happen for a few reasons.

   |Producer Connectivity Loss|


#. In order to isolate this problem to the producer, check the status of the |ccloud| cluster, specifically that it is accepting requests. Navigate to the ``Confluent Cloud`` dashboard.

#. Look at the top panels, they should all be green which means the cluster is operating safely within its resources.

   |Confluent Cloud Panel|

   For a connectivity problem in a client, look specifically at the ``Requests (rate)``. If this value
   were yellow or red, the client connectivity problem could be due to hitting the |ccloud|
   requests rate limit. If you exceed the maximum, requests may be refused. See the
   :ref:`General Request Rate Limits scenario <ccloud-observability-general-request-rate-limits>` for more details.

#. Check the producer logs for more information about what is going wrong. Use the following docker command to get the producer logs:

   .. code-block:: bash

      docker-compose logs producer

#. Verify that you see log messages similar to what is shown below:

   .. code-block:: text

      producer           | [2021-02-11 18:16:12,231] WARN [Producer clientId=producer-1] Got error produce response with correlation id 15603 on topic-partition demo-topic-1-3, retrying (2147483646 attempts left). Error: NETWORK_EXCEPTION (org.apache.kafka.clients.producer.internals.Sender)
      producer           | [2021-02-11 18:16:12,232] WARN [Producer clientId=producer-1] Received invalid metadata error in produce request on partition demo-topic-1-3 due to org.apache.kafka.common.errors.NetworkException: The server disconnected before a response was received.. Going to request metadata update now (org.apache.kafka.clients.producer.internals.Sender)

   Note that the logs provide a clear picture of what is going on--``Error: NETWORK_EXCEPTION`` and ``server disconnected``. This was expected because the failure scenario we introduced blocked outgoing traffic to the broker's port. Looking at metrics alone won't always lead you directly to an answer but they are a quick way to see if things are working as expected.

^^^^^^^^^^^^^^^^^^^^^^^^
Resolve failure scenario
^^^^^^^^^^^^^^^^^^^^^^^^

#. Remove the rule we created earlier that blocked traffic with the following command:

   .. code-block:: bash

      docker-compose exec producer iptables -D OUTPUT -p tcp --dport 9092 -j DROP

#. It may take a few minutes for the producer to start sending requests again.

^^^^^^^^^^^^^^^
Troubleshooting
^^^^^^^^^^^^^^^

#. Producer output rate doesn't come back up after adding in the ``iptables`` rule.

   Restart the producer by running ``docker-compose restart producer``. This is advice specific to this tutorial.


   |Confluent Cloud Panel|

.. |Producer Connectivity Loss|
   image:: images/producer-connectivity-loss.png
   :alt: Producer Connectivity Loss

.. _ccloud-observability-producer-authorization-problem:

Authorization Revoked
^^^^^^^^^^^^^^^^^^^^^

Using the |ccloud| CLI, revoke the producer's authorization to write to the topic.

This scenario will look at |ccloud| metrics from the Metrics API and client metrics from the client application's MBean object ``kafka.producer:type=producer-metrics,client-id=producer-1``.

^^^^^^^^^^^^^^^^^^^^^^^^^^
Introduce failure scenario
^^^^^^^^^^^^^^^^^^^^^^^^^^

#. Delete the ACL that allowed the service account to write to the topic, inserting your service account ID instead of ``184498``:

   .. code-block:: bash

      ccloud kafka acl delete --service-account 184498 --operation write  --topic '*' --allow

^^^^^^^^^^^^^^^^^^^^
Diagnose the problem
^^^^^^^^^^^^^^^^^^^^

#. From your web browser, navigate to the Grafana dashboard at http://localhost:3000 and login with the username ``admin`` and password ``password``.

#. Navigate to the ``Producer Client Metrics`` dashboard.  Wait 2 minutes and then observe:

   -  The top level panel with ``Record error rate`` (``record-error-rate``) should turn red, a major indication something is wrong.

   -  Throughput, e.g. ``Outgoing byte rate`` (``outgoing-byte-rate``), shows the producer is successfully sending messages to the broker.  This is technically correct: the producer _is_ sending the batch of records to the cluster but they are not being written to the broker's log because of lack of authorization.

   |Producer Authorization Problem|

#. Check the status of the |ccloud| cluster, specifically that it is accepting requests. Navigate to the ``Confluent Cloud`` dashboard.

#. In the ``Confluent Cloud`` dashboard, look at the top panels, they should all be green which means the cluster is operating safely within its resources.

   |Confluent Cloud Panel|

#. Change the topics filter to show only ``demo-topic-1``.  Observe:

   - ``Topic received bytes`` (``io.confluent.kafka.server/received_bytes``) is still high because it |ccloud| is still receiving the records and using network bandwidth, before they get denied due to authorization errors.

   - ``Topic retained bytes`` (``io.confluent.kafka.server/retained_bytes``) has flattened because the records sent by the producer are not getting written to the log.

   - ``Topic sent bytes`` (``io.confluent.kafka.server/sent_bytes``), which are the records sent to the consumer, has dropped to zero because there are no new records to send.

   |Confluent Cloud Dashboard Producer Authorization Problem|

#. Check the producer logs for more information about what is going wrong. Use the following docker command to get the producer logs:

   .. code-block:: bash

      docker-compose logs producer

#. Verify that you see log messages similar to what is shown below:

   .. code-block:: text

      org.apache.kafka.common.errors.TopicAuthorizationException: Not authorized to access topics: [demo-topic-1]

   Note that the logs provide a clear picture of what is going on--``org.apache.kafka.common.errors.TopicAuthorizationException``.  This was expected because the failure scenario we introduced removed the ACL that permitted the service account to write to the topic.

#. View the source code that catches this exception, :devx-examples:`ccloud-observability/src|ccloud-observability/src/main/java/io/confluent/examples/clients/cloud/ProducerExample.java`, using a ``Callback()``.

   .. code-block:: java

        producer.send(new ProducerRecord<String, PageviewRecord>(topic, key, record), new Callback() {
            @Override
            public void onCompletion(RecordMetadata m, Exception e) {
              if (e != null) {
                e.printStackTrace();
              } else {
                System.out.printf("Produced record to topic %s%n", topic);
              }
            }
        });


^^^^^^^^^^^^^^^^^^^^^^^^
Resolve failure scenario
^^^^^^^^^^^^^^^^^^^^^^^^

#. Add the ACL allowing write to the topic, inserting your service account ID instead of ``184498``:

   .. code-block:: bash

      ccloud kafka acl create --service-account 184498 --operation write  --topic '*' --allow

#. Verify that the ``org.apache.kafka.common.errors.TopicAuthorizationException`` log messages stopped in the ``producer`` container.

   .. code-block:: bash

      docker-compose logs producer


.. |Confluent Cloud Panel|
   image:: images/cloud-panel.png
   :alt: Confluent Cloud Panel

.. |Confluent Cloud Dashboard Producer Authorization Problem|
   image:: images/confluent-cloud-dashboard-producer-authorization-problem.png
   :alt: Confluent Cloud Dashboard

.. |Producer Authorization Problem|
   image:: images/producer-authorization-problem.png
   :alt: Producer Authorization Problem

