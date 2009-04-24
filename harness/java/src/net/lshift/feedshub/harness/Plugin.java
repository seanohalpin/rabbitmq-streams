package net.lshift.feedshub.harness;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URL;

import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

import com.fourspaces.couchdb.Database;
import com.fourspaces.couchdb.Document;
import com.fourspaces.couchdb.Session;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.AMQP.BasicProperties;

public abstract class Plugin {

	final protected Connection messageServerConnection;
	final protected Channel messageServerChannel;
	final private String controlQueue;
	final protected JSONObject pluginType;
	final protected JSONObject config;
	final protected JSONObject configuration;
	final private Database stateDb;
	final private String stateDocName;
	final protected Database privateDb;

	protected Plugin(final int pid, final JSONObject config) throws IOException {
		this.config = config;
		pluginType = config.getJSONObject("plugin_type");
		JSONArray globalConfig = pluginType
				.getJSONArray("global_configuration");
		JSONObject mergedConfig = new JSONObject();
		for (Object configItem : globalConfig) {
			JSONObject item = (JSONObject) configItem;
			mergedConfig.put(item.getString("name"), JSONObject.fromObject(item
					.get("value")));
		}
		JSONObject userConfig = config.getJSONObject("configuration");
		mergedConfig.putAll(userConfig);
		this.configuration = mergedConfig;

		JSONObject messageServerSpec = config.getJSONObject("messageserver");
		messageServerConnection = AMQPConnection
				.amqConnectionFromConfig(messageServerSpec);
		messageServerChannel = messageServerConnection.createChannel();

		URL dbURL = new URL(config.getString("state"));
		String path = dbURL.getPath();
		int loc = path.lastIndexOf('/');
		String db = path.substring(0, loc);

		Session couchSession = new Session(dbURL.getHost(), dbURL.getPort(),
				"", "");
		stateDocName = path.substring(1 + loc);
		stateDb = couchSession.getDatabase(db);

		Database privDb = null;
		if (config.has("database")
				&& !JSONNull.getInstance().equals(
						JSONObject.fromObject(config.get("database")))) {
			String privDbStr = config.getString("database");
			privDb = couchSession.createDatabase(privDbStr);
		}
		privateDb = privDb;

		String controlExchange = config.getString("control_exchange");
		controlQueue = messageServerChannel.queueDeclare("", false, false,
				true, true, null).getQueue();
		messageServerChannel.queueBind(controlQueue, controlExchange,
				"from_orchestrator");

		messageServerChannel.basicPublish(controlExchange, "from_plugin",
				new BasicProperties(), String.valueOf(pid).getBytes());
		messageServerChannel.basicConsume(controlQueue, new Consumer() {

			public void handleCancelOk(String consumerTag) {
			}

			public void handleConsumeOk(String consumerTag) {
			}

			public void handleDelivery(String arg0, Envelope envelope,
					BasicProperties arg2, byte[] arg3) throws IOException {
				try {
					messageServerChannel.basicAck(envelope.getDeliveryTag(),
							false);
					Plugin.this.shutdown();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					System.exit(0);
				}
			}

			public void handleShutdownSignal(String consumerTag,
					ShutdownSignalException sig) {
				try {
					Plugin.this.shutdown();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					System.exit(0);
				}
			}
		});
	}

	protected Document getState() throws IOException {
		return stateDb.getDocument(stateDocName);
	}

	protected void setState(Document state) throws IOException {
		stateDb.saveDocument(state, stateDocName);
	}

	protected final void init() throws Exception {

		JSONArray inputsAry = config.getJSONArray("inputs");
		JSONArray inputTypesAry = pluginType
				.getJSONArray("inputs_specification");

		for (int idx = 0; idx < inputsAry.size() && idx < inputTypesAry.size(); ++idx) {
			final String fieldName = inputTypesAry.getJSONObject(idx)
					.getString("name");
			Consumer callback = new Consumer() {

				private final Field pluginQueueField = Plugin.this.getClass()
						.getField(fieldName);

				public void handleCancelOk(String consumerTag) {
					try {
						Object consumer = pluginQueueField.get(Plugin.this);
						((Consumer) consumer).handleCancelOk(consumerTag);
					} catch (IllegalArgumentException e) {
						e.printStackTrace();
						System.exit(1);
					} catch (IllegalAccessException e) {
						e.printStackTrace();
						System.exit(1);
					}
				}

				public void handleConsumeOk(String consumerTag) {
					try {
						Object consumer = pluginQueueField.get(Plugin.this);
						((Consumer) consumer).handleConsumeOk(consumerTag);
					} catch (IllegalArgumentException e) {
						e.printStackTrace();
						System.exit(1);
					} catch (IllegalAccessException e) {
						e.printStackTrace();
						System.exit(1);
					}
				}

				public void handleDelivery(String arg0, Envelope arg1,
						BasicProperties arg2, byte[] arg3) throws IOException {
					try {
						Object consumer = pluginQueueField.get(Plugin.this);
						((Consumer) consumer).handleDelivery(arg0, arg1, arg2,
								arg3);
					} catch (IllegalArgumentException e) {
						e.printStackTrace();
						System.exit(1);
					} catch (IllegalAccessException e) {
						e.printStackTrace();
						System.exit(1);
					}
				}

				public void handleShutdownSignal(String consumerTag,
						ShutdownSignalException sig) {
					try {
						Object consumer = pluginQueueField.get(Plugin.this);
						((Consumer) consumer).handleShutdownSignal(consumerTag,
								sig);
					} catch (IllegalArgumentException e) {
						e.printStackTrace();
						System.exit(1);
					} catch (IllegalAccessException e) {
						e.printStackTrace();
						System.exit(1);
					}
				}

			};
			messageServerChannel.basicConsume(inputsAry.getString(idx), true,
					callback);
		}

		JSONArray outputsAry = config.getJSONArray("outputs");
		JSONArray outputTypesAry = pluginType
				.getJSONArray("outputs_specification");

		final BasicProperties blankBasicProps = new BasicProperties();
		blankBasicProps.deliveryMode = 2; // persistent
		for (int idx = 0; idx < outputsAry.size()
				&& idx < outputTypesAry.size(); ++idx) {
			final String exchange = outputsAry.getString(idx);
			final Publisher publisher = new Publisher() {

				public void publish(byte[] body) throws IOException {
					messageServerChannel.basicPublish(exchange, "",
							blankBasicProps, body);
				}

				public void acknowledge(long deliveryTag) throws IOException {
					messageServerChannel.basicAck(deliveryTag, false);
				}
			};
			Field outputField = Plugin.this.getClass().getField(
					outputTypesAry.getJSONObject(idx).getString("name"));
			outputField.set(Plugin.this, publisher);
		}
	}

	public void shutdown() throws IOException {
		messageServerChannel.queueDelete(controlQueue);
		messageServerChannel.close();
		messageServerConnection.close();
	}

	public final void run() throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				System.in));
		while (null != reader.readLine()) {
		}
		shutdown();
	}

}
