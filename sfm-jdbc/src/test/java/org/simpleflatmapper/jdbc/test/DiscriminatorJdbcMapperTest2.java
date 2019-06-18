package org.simpleflatmapper.jdbc.test;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.simpleflatmapper.jdbc.JdbcMapper;
import org.simpleflatmapper.map.getter.DiscriminatorResultSetGetter;
import org.simpleflatmapper.reflect.meta.PropertyMeta;
import org.simpleflatmapper.reflect.meta.SubPropertyMeta;
import org.simpleflatmapper.util.ListCollector;

public class DiscriminatorJdbcMapperTest2 {

	@Test
	public void test2AssFieldWithSameTypeDiscriminatorNoAsm() throws Exception {
		JdbcMapper<Foo> mapper = JdbcMapperFactoryHelper.noAsm().addKeys("id", "pFirst_id", "pSecond_id")//
				.discriminator(Parent.class, new CamelCasePathDiscriminatorResultSetGetter("class_id", Integer.class),
						builder -> builder.when(1, Parent.class).when(2, ChildA.class).when(3, ChildB.class))
				.newMapper(Foo.class);

		validateMapper(mapper);

	}

	private class CamelCasePathDiscriminatorResultSetGetter extends DiscriminatorResultSetGetter<Integer> {

		public CamelCasePathDiscriminatorResultSetGetter(String discriminatorColumn, Class<Integer> discriminatorType) {
			super(discriminatorColumn, discriminatorType);
		}

		@Override
		public Integer get(ResultSet target, PropertyMeta<?, ?> owner) throws Exception {
			return target.getObject(getCompleteDiscriminatorColumnName(owner), getDiscriminatorType());
		}

		private String getCompleteDiscriminatorColumnName(PropertyMeta<?, ?> owner) {
			if (owner == null) {
				return getDiscriminatorColumn();
			}
			String camelCaseOwnerPath = getCamelCaseOwnerPath(owner);
			return camelCaseOwnerPath + "_" + getDiscriminatorColumn();
		}

		private String getCamelCaseOwnerPath(PropertyMeta<?, ?> owner) {
			if (owner instanceof SubPropertyMeta) {
				SubPropertyMeta<?, ?, ?> subPropertyMeta = (SubPropertyMeta<?, ?, ?>) owner;
				return getCamelCaseOwnerPath(subPropertyMeta.getOwnerProperty())
						+ capitalize(subPropertyMeta.getSubProperty().getName());
			}
			return owner.getName();
		}

		private String capitalize(String string) {
			return string.substring(0, 1).toUpperCase() + string.substring(1);
		}
	}

	private void validateMapper(JdbcMapper<Foo> mapper) throws Exception {
		List<Foo> is = mapper.forEach(setUpResultSetMock(), new ListCollector<Foo>()).getList();
		assertTrue(is.get(0).pFirst instanceof ChildA);
		assertTrue(is.get(0).pSecond instanceof ChildB);
		ChildA childA = (ChildA)is.get(0).pFirst;
		assertTrue(childA.getParent() instanceof ChildA);
		ChildA parentChildA = (ChildA)childA.getParent();
		assertTrue(parentChildA.getParent() instanceof ChildB);

		assertTrue(is.get(1).pFirst instanceof ChildA);
		assertTrue(is.get(1).pSecond instanceof Parent);
		assertTrue(((ChildA)is.get(1).pFirst).getParent() instanceof ChildB);
	}

	private ResultSet setUpResultSetMock() throws SQLException {
		ResultSet rs = mock(ResultSet.class);

		ResultSetMetaData metaData = mock(ResultSetMetaData.class);

		final String[] columns = new String[] { "id", //
				"pFirst_id", "pFirst_class_id", "pFirst_a_string", "pFirst_b_string", //
				"pSecond_id", "pSecond_class_id", "pSecond_a_string", "pSecond_b_string", //
				"pFirstParent_id", "pFirstParent_class_id", "pFirstParent_a_string", "pFirstParent_b_string",//
				"pFirstParentParent_id", "pFirstParentParent_class_id", "pFirstParentParent_a_string", "pFirstParentParent_b_string"};

		when(metaData.getColumnCount()).thenReturn(columns.length);
		when(metaData.getColumnLabel(anyInt())).then(new Answer<String>() {
			@Override
			public String answer(InvocationOnMock invocationOnMock) throws Throwable {
				return columns[-1 + (Integer) invocationOnMock.getArguments()[0]];
			}
		});

		when(rs.getMetaData()).thenReturn(metaData);

		final AtomicInteger ai = new AtomicInteger();

		final Object[][] rows = new Object[][] { //
				{ 1, 7, 2, "aString", null, 2, 3, null, "bString", 4, 2, "aParent", null, 6, 3, null, "bParentParent" },
				{ 2, 8, 2, "aString", null, 3, 1, null, null, 5, 3, null, "bParent", null, null, null, null } };

		when(rs.next()).then(new Answer<Boolean>() {
			@Override
			public Boolean answer(InvocationOnMock invocationOnMock) throws Throwable {
				return ai.getAndIncrement() < rows.length;
			}
		});
		final Answer<Object> getValue = new Answer<Object>() {
			@Override
			public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
				final Object[] row = rows[ai.get() - 1];
				final Integer col = -1 + (Integer) invocationOnMock.getArguments()[0];
				return (row[col]);
			}
		};

		final Answer<Object> getColumnValue = new Answer<Object>() {
			@Override
			public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
				final Object[] row = rows[ai.get() - 1];
				final String col = (String) invocationOnMock.getArguments()[0];
				return (row[Arrays.asList(columns).indexOf(col)]);
			}
		};

		when(rs.getInt(anyInt())).then(getValue);
		when(rs.getString(anyInt())).then(getValue);
		when(rs.getString(any(String.class))).then(getColumnValue);
		when(rs.getObject(anyInt())).then(getValue);
		when(rs.getObject(anyString(), any(Class.class))).then(getColumnValue);

		return rs;
	}

	public class Parent {
		int id;
		Integer classId;

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public Integer getClassId() {
			return classId;
		}

		public void setClassId(Integer classId) {
			this.classId = classId;
		}

	}

	public class ChildA extends Parent {
		String aString;
		Parent parent;

		public String getaString() {
			return aString;
		}

		public void setaString(String aString) {
			this.aString = aString;
		}

		public Parent getParent() {
			return parent;
		}

		public void setParent(Parent parent) {
			this.parent = parent;
		}

	}

	public class ChildB extends Parent {
		String bString;

		public String getbString() {
			return bString;
		}

		public void setbString(String bString) {
			this.bString = bString;
		}
	}

	public class Foo {
		int id;
		Parent pFirst;
		Parent pSecond;

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public Parent getpFirst() {
			return pFirst;
		}

		public void setpFirst(Parent pFirst) {
			this.pFirst = pFirst;
		}

		public Parent getpSecond() {
			return pSecond;
		}

		public void setpSecond(Parent pSecond) {
			this.pSecond = pSecond;
		}

	}
}
