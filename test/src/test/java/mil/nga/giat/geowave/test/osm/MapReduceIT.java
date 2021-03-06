package mil.nga.giat.geowave.test.osm;

import java.io.File;

import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.ZooKeeperInstance;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.security.Authorizations;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mil.nga.giat.geowave.cli.osm.operations.IngestOSMToGeoWaveCommand;
import mil.nga.giat.geowave.cli.osm.operations.StageOSMToHDFSCommand;
import mil.nga.giat.geowave.core.cli.parser.ManualOperationParams;
import mil.nga.giat.geowave.core.store.operations.remote.options.DataStorePluginOptions;
import mil.nga.giat.geowave.datastore.accumulo.operations.config.AccumuloRequiredOptions;
import mil.nga.giat.geowave.test.AccumuloStoreTestEnvironment;
import mil.nga.giat.geowave.test.GeoWaveITRunner;
import mil.nga.giat.geowave.test.TestUtils;
import mil.nga.giat.geowave.test.annotation.Environments;
import mil.nga.giat.geowave.test.annotation.GeoWaveTestStore;
import mil.nga.giat.geowave.test.annotation.Environments.Environment;
import mil.nga.giat.geowave.test.annotation.GeoWaveTestStore.GeoWaveStoreType;
import mil.nga.giat.geowave.test.mapreduce.MapReduceTestEnvironment;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

@RunWith(GeoWaveITRunner.class)
@Environments({
	Environment.MAP_REDUCE
})
public class MapReduceIT
{

	private final static Logger LOGGER = LoggerFactory.getLogger(MapReduceIT.class);

	protected static final String TEST_RESOURCE_DIR = new File(
			"./src/test/resources/osm/").getAbsolutePath().toString();
	protected static final String TEST_DATA_ZIP_RESOURCE_PATH = TEST_RESOURCE_DIR + "/" + "andorra-latest.zip";
	protected static final String TEST_DATA_BASE_DIR = new File(
			TestUtils.TEST_CASE_BASE,
			"osm").getAbsoluteFile().toString();

	@GeoWaveTestStore({
		GeoWaveStoreType.ACCUMULO,
		GeoWaveStoreType.BIGTABLE,
		GeoWaveStoreType.HBASE
	})
	protected DataStorePluginOptions dataStoreOptions;

	@BeforeClass
	public static void setupTestData()
			throws ZipException {
		ZipFile data = new ZipFile(
				new File(
						TEST_DATA_ZIP_RESOURCE_PATH));
		data.extractAll(TEST_DATA_BASE_DIR);

	}

	@Test
	public void testIngestOSMPBF()
			throws Exception {
		TestUtils.deleteAll(dataStoreOptions);
		// NOTE: This will probably fail unless you bump up the memory for the
		// tablet
		// servers, for whatever reason, using the
		// miniAccumuloConfig.setMemory() function.
		MapReduceTestEnvironment mrEnv = MapReduceTestEnvironment.getInstance();

		// TODO: for now this only works with accumulo, generalize the data
		// store usage
		AccumuloStoreTestEnvironment accumuloEnv = AccumuloStoreTestEnvironment.getInstance();

		String hdfsPath = mrEnv.getHdfsBaseDirectory() + "/osm_stage/";

		StageOSMToHDFSCommand stage = new StageOSMToHDFSCommand();
		stage.setParameters(
				TEST_DATA_BASE_DIR,
				mrEnv.getHdfs(),
				hdfsPath);
		stage.execute(new ManualOperationParams());

		Connector conn = new ZooKeeperInstance(
				accumuloEnv.getAccumuloInstance(),
				accumuloEnv.getZookeeper()).getConnector(
				accumuloEnv.getAccumuloUser(),
				new PasswordToken(
						accumuloEnv.getAccumuloPassword()));
		Authorizations auth = new Authorizations(
				new String[] {
					"public"
				});
		conn.securityOperations().changeUserAuthorizations(
				accumuloEnv.getAccumuloUser(),
				auth);

		IngestOSMToGeoWaveCommand ingest = new IngestOSMToGeoWaveCommand();
		ingest.setParameters(
				mrEnv.getHdfs(),
				hdfsPath,
				null);
		ingest.setInputStoreOptions(dataStoreOptions);

		ingest.getIngestOptions().setJobName(
				"ConversionTest");

		// Execute for node's ways, and relations.
		ingest.getIngestOptions().setMapperType(
				"NODE");
		ingest.execute(new ManualOperationParams());
		System.out.println("finished accumulo ingest Node");

		ingest.getIngestOptions().setMapperType(
				"WAY");
		ingest.execute(new ManualOperationParams());
		System.out.println("finished accumulo ingest Way");

		ingest.getIngestOptions().setMapperType(
				"RELATION");
		ingest.execute(new ManualOperationParams());
		System.out.println("finished accumulo ingest Relation");
	}
}
