package org.simpleflatmapper.map.mapper;

import org.simpleflatmapper.map.FieldKey;
import org.simpleflatmapper.map.FieldMapper;
import org.simpleflatmapper.map.MapperBuilderErrorHandler;
import org.simpleflatmapper.map.MapperBuildingException;
import org.simpleflatmapper.map.MapperConfig;
import org.simpleflatmapper.map.MappingContext;
import org.simpleflatmapper.map.SourceFieldMapper;
import org.simpleflatmapper.map.context.MappingContextFactory;
import org.simpleflatmapper.map.context.MappingContextFactoryBuilder;
import org.simpleflatmapper.map.getter.DiscriminatorResultSetGetter;
import org.simpleflatmapper.map.impl.DiscriminatorPropertyFinder;
import org.simpleflatmapper.map.property.OptionalProperty;
import org.simpleflatmapper.reflect.BiInstantiator;
import org.simpleflatmapper.reflect.Getter;
import org.simpleflatmapper.reflect.meta.ClassMeta;
import org.simpleflatmapper.reflect.meta.PropertyFinder;
import org.simpleflatmapper.reflect.meta.PropertyMeta;
import org.simpleflatmapper.reflect.meta.SubPropertyMeta;
import org.simpleflatmapper.util.BiConsumer;
import org.simpleflatmapper.util.EqualsPredicate;
import org.simpleflatmapper.util.ForEachCallBack;
import org.simpleflatmapper.util.Predicate;
import org.simpleflatmapper.util.TypeHelper;

import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;


public class DiscriminatorConstantSourceMapperBuilder<S, T, K extends FieldKey<K>>  extends ConstantSourceMapperBuilder<S, T, K> {
    
    private final DiscriminatedBuilder<S, T, K>[] builders;
    private final MappingContextFactoryBuilder<S, K> mappingContextFactoryBuilder;
    private final CaptureError mapperBuilderErrorHandler;
    private final MapperConfig<K, ? extends S> mapperConfig;

    @SuppressWarnings("unchecked")
    public DiscriminatorConstantSourceMapperBuilder(
            MapperConfig.Discriminator<? super S, T> discriminator,
            final MapperSource<? super S, K> mapperSource,
            final ClassMeta<T> classMeta,
            final MapperConfig<K, ? extends S> mapperConfig,
            MappingContextFactoryBuilder<S, K> mappingContextFactoryBuilder,
            KeyFactory<K> keyFactory,
            PropertyFinder<T> propertyFinder) throws MapperBuildingException {
        this.mappingContextFactoryBuilder = mappingContextFactoryBuilder;
        this.mapperConfig = mapperConfig;
        builders = new DiscriminatedBuilder[discriminator.cases.length];

        mapperBuilderErrorHandler = new CaptureError(mapperConfig.mapperBuilderErrorHandler(), builders.length);
        MapperConfig<K, ? extends S> kMapperConfig = mapperConfig.mapperBuilderErrorHandler(mapperBuilderErrorHandler);
        
        for(int i = 0; i < discriminator.cases.length; i++) {
            MapperConfig.DiscriminatorCase<? super S, ? extends T> discriminatorCase = discriminator.cases[i];

            PropertyFinder<T> subPropertyFinder = propertyFinder;
            
            if (propertyFinder instanceof DiscriminatorPropertyFinder) {
                subPropertyFinder = ((DiscriminatorPropertyFinder<T>)subPropertyFinder).getImplementationPropertyFinder(discriminatorCase.classMeta.getType());
            }
            
            builders[i] = getDiscriminatedBuilder(mapperSource, mappingContextFactoryBuilder, keyFactory, subPropertyFinder, kMapperConfig, discriminatorCase, classMeta);
        }
    }

    private <T> DiscriminatedBuilder<S, T, K> getDiscriminatedBuilder(MapperSource<? super S, K> mapperSource, MappingContextFactoryBuilder<S, K> mappingContextFactoryBuilder, KeyFactory<K> keyFactory, PropertyFinder<T> propertyFinder, MapperConfig<K, ? extends S> kMapperConfig, MapperConfig.DiscriminatorCase<? super S, ? extends T> discrimnatorCase, ClassMeta<T> commonClassMeta) {
        return new DiscriminatedBuilder<S, T, K>((MapperConfig.DiscriminatorCase<? super S, T>) discrimnatorCase, 
                new DefaultConstantSourceMapperBuilder<S, T, K>(mapperSource, (ClassMeta<T>) discrimnatorCase.classMeta.withReflectionService(commonClassMeta.getReflectionService()), kMapperConfig, mappingContextFactoryBuilder, keyFactory, propertyFinder));
    }

    @Override
    public ConstantSourceMapperBuilder<S, T, K> addMapping(K key, ColumnDefinition<K, ?> columnDefinition) {
        for(DiscriminatedBuilder<S, T, K> builder : builders) {
            builder.builder.addMapping(key, columnDefinition);
        }
        final ColumnDefinition<K, ?> composedDefinition = columnDefinition.compose(mapperConfig.columnDefinitions().getColumnDefinition(key));
        mapperBuilderErrorHandler.successfullyMapAtLeastToOne(composedDefinition);
        return this;
    }

    @Override
    protected <P> void addMapping(final K columnKey, final ColumnDefinition<K, ?> columnDefinition, final PropertyMeta<T, P> prop) {

        if (prop instanceof DiscriminatorPropertyFinder.DiscriminatorPropertyMeta) {
            DiscriminatorPropertyFinder.DiscriminatorPropertyMeta pm = (DiscriminatorPropertyFinder.DiscriminatorPropertyMeta) prop;
            pm.forEachProperty(new BiConsumer<Type, PropertyMeta<?, ?>>() {
                @Override
                public void accept(Type type, PropertyMeta<?, ?> propertyMeta) {
                    getBuilder(type).addMapping(columnKey, columnDefinition, propertyMeta);
                }
            });
            
        } else {
            for (DiscriminatedBuilder<S, T, K> builder : builders) {
                    builder.builder.addMapping(columnKey, columnDefinition, prop);
            }
        }
        
    }

    private ConstantSourceMapperBuilder getBuilder(Type type) {
        for (DiscriminatedBuilder<S, T, K> builder : builders) {
            if (TypeHelper.areEquals(builder.builder.getTargetType(), type)) {
                return builder.builder;
            }
        }
        throw new IllegalArgumentException("Unknown type " + type);
    }

    @Override
    public List<K> getKeys() {
        HashSet<K> keys = new HashSet<K>();
        for(DiscriminatedBuilder<S, T, K> builder : builders) {
            keys.addAll(builder.builder.getKeys());
        }
        return new ArrayList<K>(keys);
    }

    @Override
    public <H extends ForEachCallBack<PropertyMapping<T, ?, K>>> H forEachProperties(H handler) {
        for(DiscriminatedBuilder<S, T, K> builder : builders) {
            builder.builder.forEachProperties(handler);
        }
        return handler;
    }

    @Override
    public ContextualSourceFieldMapperImpl<S, T> mapper() {
        SourceFieldMapper<S, T> mapper = sourceFieldMapper();
        return new ContextualSourceFieldMapperImpl<S, T>(mappingContextFactoryBuilder.build(), mapper);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public SourceFieldMapper<S, T> sourceFieldMapper() {
        PredicatedInstantiator<S, T>[] predicatedInstantiator = new PredicatedInstantiator[builders.length];
        
        
        List<FieldMapper<S, T>> fieldMappers = new ArrayList<FieldMapper<S, T>>();
        for(int i = 0; i < builders.length; i++) {
            DiscriminatedBuilder<S, T, K> builder = builders[i];
            final Predicate<? super S> predicate = builder.discrimnatorCase.predicate;
            DefaultConstantSourceMapperBuilder.GenericBuilderMapping genericBuilderMapping = builder.builder.getGenericBuilderMapping();
            predicatedInstantiator[i] = new PredicatedInstantiator<S, T>(predicate, genericBuilderMapping.genericBuilderInstantiator);

            final FieldMapper[] targetFieldMappers = genericBuilderMapping.targetFieldMappers;
            
            fieldMappers.add(new FieldMapper<S, T>() {
                @Override
                public void mapTo(S source, T target, MappingContext<? super S> context) throws Exception {
                    if (predicate.test(source)) {
                        for(FieldMapper fm : targetFieldMappers) {
                            fm.mapTo(source, target, context);
                        }
                    }
                }
            });
        }
        boolean oneColumn = isOneColumn(predicatedInstantiator);
        BiInstantiator<S, MappingContext<? super S>,  GenericBuilder<S, T>> gbi =
                oneColumn ? 
                        new OneColumnBuildBiInstantiator<S, T>(predicatedInstantiator, createSubProperty(mappingContextFactoryBuilder)) :        
                    new GenericBuildBiInstantiator<S, T>(predicatedInstantiator);

        DiscriminatorGenericBuilderMapper<S, T> mapper = new DiscriminatorGenericBuilderMapper<S, T>(gbi);

        FieldMapper<S, T>[] targetFieldMappers = fieldMappers.toArray(new FieldMapper[0]);
        //
        
        return new TransformSourceFieldMapper<S, GenericBuilder<S, T>, T>(mapper, targetFieldMappers, GenericBuilder.<S, T>buildFunction());
    }

	private PropertyMeta<?, ?> createSubProperty(MappingContextFactoryBuilder mappingContextFactoryBuilder){
        if (mappingContextFactoryBuilder.getParent().getOwner() != null) {
            return new SubPropertyMeta<>(mappingContextFactoryBuilder.getOwner().getReflectService(), createSubProperty(mappingContextFactoryBuilder.getParent()), mappingContextFactoryBuilder.getOwner());
        }
        return mappingContextFactoryBuilder.getOwner();
	}
    
    private boolean isOneColumn(PredicatedInstantiator<S, T>[] predicatedInstantiator) {
        
        Getter getter = null;
        for(PredicatedInstantiator<S, T> pi : predicatedInstantiator) {
            if (!(pi.predicate instanceof AbstractMapperFactory.DiscriminatorConditionBuilder.SourcePredicate)) {
                return false;
            }
            AbstractMapperFactory.DiscriminatorConditionBuilder.SourcePredicate sp = (AbstractMapperFactory.DiscriminatorConditionBuilder.SourcePredicate) pi.predicate;
            Getter lg = sp.getter;
            if (getter == null) {
                getter = lg;
            } else if (getter != lg) return false;
            
            
            if (!(sp.predicate instanceof EqualsPredicate)) return false;
        }
        return true;
    }

    @Override
    public boolean isRootAggregate() {
        return builders[0].builder.isRootAggregate();
    }

    @Override
    public MappingContextFactory<? super S> contextFactory() {
        return builders[0].builder.contextFactory();
    }

    @Override
    public void addMapper(FieldMapper<S, T> mapper) {
        for(DiscriminatedBuilder<S, T, K> builder : builders) {
            builder.builder.addMapper(mapper);
        }
    }


    private static class DiscriminatedBuilder<S, T, K extends FieldKey<K>> {
        private final MapperConfig.DiscriminatorCase<? super S, T> discrimnatorCase;
        private final DefaultConstantSourceMapperBuilder<S, T, K> builder;

        private DiscriminatedBuilder(MapperConfig.DiscriminatorCase<? super S, T> discrimnatorCase, DefaultConstantSourceMapperBuilder<S, T, K> builder) {
            this.discrimnatorCase = discrimnatorCase;
            this.builder = builder;
        }
    }

    private static class GenericBuildBiInstantiator<S, T> implements BiInstantiator<S, MappingContext<? super S>, GenericBuilder<S, T>> {
        private final PredicatedInstantiator<S, T>[] predicatedInstantiators;

        public GenericBuildBiInstantiator(PredicatedInstantiator<S, T>[] predicatedInstantiators) {
            this.predicatedInstantiators = predicatedInstantiators;
        }

        @SuppressWarnings("unchecked")
        @Override
        public GenericBuilder<S, T> newInstance(S o, MappingContext<? super S> o2) throws Exception {
            for(PredicatedInstantiator<S, T> pi : predicatedInstantiators) {
                //noinspection unchecked
                if (pi.predicate.test(o)) {
                    return pi.instantiator.newInstance(o, o2);
                }
            }
            throw new IllegalArgumentException("No discrimator matched " + o); 
        }
    }
    
    private static class OneColumnBuildBiInstantiator<S, T> implements BiInstantiator<S, MappingContext<? super S>, GenericBuilder<S, T>> {
        private final Getter<S, ?> getter;
        private final Map<Object, BiInstantiator<S, MappingContext<? super S>, GenericBuilder<S, T>>> instantiators;
        private final PropertyMeta<?, ?> owner;
        
        public OneColumnBuildBiInstantiator(PredicatedInstantiator<S, T>[] predicatedInstantiators,	PropertyMeta<?, ?> owner) {
            if (predicatedInstantiators == null || predicatedInstantiators.length == 0) throw new IllegalArgumentException("predicatedInstantiators is null or empty");
            getter = ((AbstractMapperFactory.DiscriminatorConditionBuilder.SourcePredicate)predicatedInstantiators[0].predicate).getter;
            
            instantiators = new HashMap<Object, BiInstantiator<S, MappingContext<? super S>, GenericBuilder<S, T>>>();
            
            for(PredicatedInstantiator<S, T> pi : predicatedInstantiators) {
                EqualsPredicate ep = (EqualsPredicate) ((AbstractMapperFactory.DiscriminatorConditionBuilder.SourcePredicate)pi.predicate).predicate;
                instantiators.put(ep.expected, pi.instantiator);
            }
            this.owner = owner;
        }

        @SuppressWarnings("unchecked")
        @Override
        public GenericBuilder<S, T> newInstance(S o, MappingContext<? super S> o2) throws Exception {
            Object value;
            if (getter instanceof DiscriminatorResultSetGetter) {
                value = ((DiscriminatorResultSetGetter) getter).get((ResultSet) o, owner);
            } else {
                value = getter.get(o);
            }
            
            BiInstantiator<S, MappingContext<? super S>, GenericBuilder<S, T>> instantiator = instantiators.get(value);
            
            if (instantiator == null)
                throw new IllegalArgumentException("No discrimator matched " + value);

            return instantiator.newInstance(o, o2);
        }
    }

    private static class PredicatedInstantiator<S, T> {
        private final Predicate predicate;
        private final BiInstantiator<S, MappingContext<? super S>, GenericBuilder<S, T>> instantiator;

        private PredicatedInstantiator(Predicate predicate, BiInstantiator<S, MappingContext<? super S>, GenericBuilder<S, T>> instantiator) {
            this.predicate = predicate;
            this.instantiator = instantiator;
        }
    }

    private class DiscriminatorGenericBuilderMapper<S, T> extends AbstractMapper<S, GenericBuilder<S, T>> {
        public DiscriminatorGenericBuilderMapper(BiInstantiator<? super S, MappingContext<? super S>,  GenericBuilder<S, T>> gbi) {
            super(gbi);
        }

        @Override
        protected void mapFields(S source, GenericBuilder<S, T> target, MappingContext<? super S> mappingContext) throws Exception {
            target.mapFrom(source, mappingContext);
        }

        @Override
        protected void mapToFields(S source, GenericBuilder<S, T> target, MappingContext<? super S> mappingContext) throws Exception {
            target.mapFrom(source, mappingContext);
        }
    }

    private static class CaptureError implements MapperBuilderErrorHandler {
        private final MapperBuilderErrorHandler delegate;
        private final List<PropertyNotFound> errorCollector;
        private final int nbBuilders;

        private CaptureError(MapperBuilderErrorHandler delegate, int nbBuilders) {
            this.delegate = delegate;
            this.nbBuilders = nbBuilders;
            errorCollector = new ArrayList<PropertyNotFound>();
        }

        @Override
        public void accessorNotFound(String msg) {
            delegate.accessorNotFound(msg);
        }

        @Override
        public void propertyNotFound(Type target, String property) {
            errorCollector.add(new PropertyNotFound(target, property));
        }

        @Override
        public void customFieldError(FieldKey<?> key, String message) {
            delegate.customFieldError(key, message);
        }
        
        public void successfullyMapAtLeastToOne(ColumnDefinition<?, ?> columnDefinition) {
            try {
                if (errorCollector.size() == nbBuilders && ! columnDefinition.has(OptionalProperty.class)) {
                    PropertyNotFound propertyNotFound = errorCollector.get(0);
                    delegate.propertyNotFound(propertyNotFound.target, propertyNotFound.property);
                }
            } finally {
                errorCollector.clear();
            }
        }
        
        private static class PropertyNotFound {
            final Type target;
            final String property;

            private PropertyNotFound(Type target, String property) {
                this.target = target;
                this.property = property;
            }
        }
        
    }
}
