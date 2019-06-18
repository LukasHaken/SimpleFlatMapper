package org.simpleflatmapper.map.getter;

import java.sql.ResultSet;

import org.simpleflatmapper.reflect.Getter;
import org.simpleflatmapper.reflect.meta.PropertyMeta;

abstract public class DiscriminatorResultSetGetter<P> implements Getter<ResultSet, P> {

	private final String discriminatorColumn;
	private final Class<P> discriminatorType;

	public DiscriminatorResultSetGetter(String discriminatorColumn, Class<P> discriminatorType) {
		super();
		this.discriminatorColumn = discriminatorColumn;
		this.discriminatorType = discriminatorType;
	}

	@Override
	public P get(ResultSet target) throws Exception {
		return get(target, null);
	}

	abstract public P get(ResultSet target, PropertyMeta<?, ?> owner) throws Exception;

	public String getDiscriminatorColumn() {
		return discriminatorColumn;
	}

	public Class<P> getDiscriminatorType() {
		return discriminatorType;
	}
}
