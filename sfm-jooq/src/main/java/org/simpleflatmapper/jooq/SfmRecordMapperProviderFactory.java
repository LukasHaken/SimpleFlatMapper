package org.simpleflatmapper.jooq;

import org.jooq.Record;
import org.simpleflatmapper.map.MapperConfig;
import org.simpleflatmapper.map.getter.ContextualGetterFactoryAdapter;
import org.simpleflatmapper.map.mapper.AbstractColumnNameDiscriminatorMapperFactory;
import org.simpleflatmapper.map.property.FieldMapperColumnDefinition;
import org.simpleflatmapper.map.mapper.AbstractColumnDefinitionProvider;
import org.simpleflatmapper.map.mapper.AbstractMapperFactory;
import org.simpleflatmapper.map.mapper.FieldMapperColumnDefinitionProviderImpl;
import org.simpleflatmapper.reflect.Getter;
import org.simpleflatmapper.util.Function;

import java.lang.reflect.Type;

public class SfmRecordMapperProviderFactory
        extends AbstractColumnNameDiscriminatorMapperFactory<JooqFieldKey, SfmRecordMapperProviderFactory, Record> {


    private static final ColumnNameGetterFactory<Record> NAMED_GETTER = new ColumnNameGetterFactory<Record>() {
        @Override
        public <T> Getter<? super Record, ? extends T> getGetter(final String discriminatorColumn, final Class<T> discriminatorType) {
            return new Getter<Record, T>() {
                @Override
                public T get(Record target) throws Exception {
                    return target.<T>getValue(discriminatorColumn, discriminatorType);
                }
            };
        }
    };

    public static SfmRecordMapperProviderFactory newInstance() {
        return new SfmRecordMapperProviderFactory();
    }

    public static SfmRecordMapperProviderFactory newInstance(
            AbstractMapperFactory<JooqFieldKey, ?, Record> config) {
        return new SfmRecordMapperProviderFactory(config);
    }

    public SfmRecordMapperProviderFactory(AbstractMapperFactory<JooqFieldKey, ?, Record> config) {
        super(config, NAMED_GETTER);
    }

    public SfmRecordMapperProviderFactory(AbstractColumnDefinitionProvider<JooqFieldKey> columnDefinitions, FieldMapperColumnDefinition<JooqFieldKey> identity) {
        super(columnDefinitions, identity, NAMED_GETTER, new ContextualGetterFactoryAdapter<Record, JooqFieldKey>(new RecordGetterFactory()));
    }

    private SfmRecordMapperProviderFactory() {
        super(new FieldMapperColumnDefinitionProviderImpl<JooqFieldKey>(), FieldMapperColumnDefinition.<JooqFieldKey>identity(), NAMED_GETTER, new ContextualGetterFactoryAdapter<Record, JooqFieldKey>(new RecordGetterFactory()));
    }

    public SfmRecordMapperProvider newProvider() {
        return new SfmRecordMapperProvider(new Function<Type, MapperConfig<JooqFieldKey, Record>>() {
            @Override
            public MapperConfig<JooqFieldKey, Record> apply(Type type) {
                return mapperConfig(type);
            }
        }, getReflectionService());
    }

}
