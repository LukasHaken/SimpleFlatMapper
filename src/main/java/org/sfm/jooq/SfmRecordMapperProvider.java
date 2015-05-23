package org.sfm.jooq;

import org.jooq.*;
import org.sfm.map.Mapper;
import org.sfm.map.impl.MapperCache;
import org.sfm.map.impl.TargetColumnsMapperKey;

/**
 * Integration point with jooq.<p>
 * Provide a JooqRecordMapper backed by an Sfm {@link org.jooq.Record} {@link org.sfm.map.Mapper}
 */
public class SfmRecordMapperProvider implements RecordMapperProvider {

	private final MapperCache<TargetColumnsMapperKey, Mapper<Record, ?>> mapperCache = new MapperCache<TargetColumnsMapperKey, Mapper<Record, ?>>();

	
	@Override
	public <R extends Record, E> RecordMapper<R, E> provide(RecordType<R> recordType, Class<? extends E> type) {
		
		TargetColumnsMapperKey key = getMapperKey(recordType, type);
		
		@SuppressWarnings("unchecked")
		Mapper<Record, E> mapper = (Mapper<Record, E>) mapperCache.get(key);
		
		if (mapper == null) {
			JooqMapperBuilder<E> mapperBuilder = new JooqMapperBuilder<E>(type);
			
			int i = 0;
			for(Field<?> field : recordType.fields()) {
				mapperBuilder.addField(new JooqFieldKey(field, i++));
			}
			
			mapper = mapperBuilder.mapper();
			mapperCache.add(key, mapper);
		}
		
		return new JooqRecordMapperWrapper<R, E>(mapper);
	}

	
	private <R extends Record> TargetColumnsMapperKey getMapperKey(RecordType<R> recordType, Class<?> type) {
		String[] columns = new String[recordType.size()];
		int i = 0;
		for(Field<?> field : recordType.fields()) {
			columns[i++] = field.getName();
		}
		
		return new TargetColumnsMapperKey(type, columns);
	}

}
