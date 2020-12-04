package com.linkedin.metadata.dao;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.linkedin.common.AuditStamp;
import com.linkedin.common.urn.Urn;
import com.linkedin.common.urn.Urns;
import com.linkedin.data.DataMap;
import com.linkedin.data.codec.JacksonDataCodec;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.data.template.UnionTemplate;
import com.linkedin.metadata.dao.producer.DummyMetadataEventProducer;
import com.linkedin.metadata.dao.utils.RecordUtils;
import io.ebean.Ebean;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nonnull;


/**
 * An immutable implementation of {@link BaseLocalDAO}. Suitable for serving statically declared metadata.
 */
public class ImmutableLocalDAO<ASPECT_UNION extends UnionTemplate, URN extends Urn>
    extends EbeanLocalDAO<ASPECT_UNION, URN> {

  private static final JacksonDataCodec JSON_CODEC = new JacksonDataCodec();

  private static final AuditStamp DUMMY_AUDIT_STAMP =
      new AuditStamp().setActor(Urns.createFromTypeSpecificString("dummy", "unknown")).setTime(0L);

  private static final String GMA_CREATE_ALL_SQL = "gma-create-all.sql";

  /**
   * Constructs an {@link ImmutableLocalDAO} from a hard-coded URN-Aspect map.
   */
  public ImmutableLocalDAO(@Nonnull Class<ASPECT_UNION> aspectUnionClass,
      @Nonnull Map<URN, ? extends RecordTemplate> urnAspectMap, @Nonnull Class<URN> urnClass) {

    super(aspectUnionClass, new DummyMetadataEventProducer<>(),
        createProductionH2ServerConfig(aspectUnionClass.getCanonicalName()), urnClass);
    _server.execute(Ebean.createSqlUpdate(readSQLfromFile(GMA_CREATE_ALL_SQL)));
    urnAspectMap.forEach((key, value) -> super.save(key, value, DUMMY_AUDIT_STAMP, LATEST_VERSION, true));
  }

  // For testing purpose
  public ImmutableLocalDAO(@Nonnull Class<ASPECT_UNION> aspectUnionClass,
      @Nonnull Map<URN, ? extends RecordTemplate> urnAspectMap, boolean ddlGenerate, @Nonnull Class<URN> urnClass) {

    super(aspectUnionClass, new DummyMetadataEventProducer<>(), createTestingH2ServerConfig(), urnClass);
    urnAspectMap.forEach((key, value) -> super.save(key, value, DUMMY_AUDIT_STAMP, LATEST_VERSION, true));
  }

  /**
   * Loads a map of URN to aspect values from an {@link InputStream}.
   *
   * <p>The InputStream is expected to contain a JSON map where the keys are a specific type of URN and values are a
   * specific type of metadata aspect.
   */
  @Nonnull
  public static <ASPECT extends RecordTemplate> Map<Urn, ASPECT> loadAspects(@Nonnull Class<ASPECT> aspectClass,
      @Nonnull InputStream inputStream) throws IOException, URISyntaxException {

    final Map<Urn, ASPECT> aspects = new HashMap<>();
    try (InputStreamReader reader = new InputStreamReader(inputStream, Charset.defaultCharset())) {
      final DataMap dataMap = JSON_CODEC.readMap(reader);
      for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
        final Urn urn = Urn.createFromString(entry.getKey());
        final Object value = entry.getValue();

        if (!DataMap.class.isAssignableFrom(value.getClass())) {
          throw new IllegalArgumentException(
              String.format("Failed to parse value for urn `%s`. Expected `DataMap` but got `%s`.", entry.getKey(),
                  value.getClass()));
        }

        final DataMap dataMapValue = (DataMap) value;
        final ASPECT aspect = RecordUtils.toRecordTemplate(aspectClass, dataMapValue);
        aspects.put(urn, aspect);
      }
    }

    return aspects;
  }

  @Override
  @Nonnull
  public <ASPECT extends RecordTemplate> ASPECT add(@Nonnull URN urn, @Nonnull Class<ASPECT> aspectClass,
      @Nonnull Function<Optional<ASPECT>, ASPECT> updateLambda, @Nonnull AuditStamp auditStamp,
      int maxTransactionRetry) {
    throw new UnsupportedOperationException("Not supported by immutable DAO");
  }

  @Override
  public long newNumericId() {
    throw new UnsupportedOperationException("Not supported by immutable DAO");
  }

  @Nonnull
  private String readSQLfromFile(@Nonnull String resourcePath) {
    try {
      return Resources.toString(Resources.getResource(resourcePath), Charsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}