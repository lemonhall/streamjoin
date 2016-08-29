package de.infonautika.streamjoin;

import de.infonautika.streamjoin.joins.InnerEquiJoin;
import de.infonautika.streamjoin.joins.JoinStrategy;
import de.infonautika.streamjoin.joins.Joiner;
import de.infonautika.streamjoin.joins.LeftOuterJoin;
import de.infonautika.streamjoin.joins.indexing.Indexer;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

public class Join {

	public static <L> LeftSide<L> join(Stream<L> left) {
		return new LeftSide<>(left, JoinType.INNER);
	}

    public static <L> LeftSide<L> leftOuter(Stream<L> left) {
        return new LeftSide<>(left, JoinType.LEFT_OUTER);
    }

    public static <L> LeftSide<L> fullOuter(Stream<L> left) {
        return new LeftSide<>(left, JoinType.FULL_OUTER);
    }

    private enum JoinType {
        INNER, LEFT_OUTER, FULL_OUTER
    }

    static class LeftSide<L> {
		private final Stream<L> left;
        private final JoinType joinType;

        public LeftSide(Stream<L> left, JoinType joinType) {
			this.left = left;
            this.joinType = joinType;
        }

		public <K> LeftKey<L, K> withKey(Function<L, K> leftKeyFunction) {
			return new LeftKey<>(leftKeyFunction, this);
		}
	}

	static class LeftKey<L, K> {

		private final LeftSide<L> leftSide;
		private final Function<L, K> leftKeyFunction;

		private LeftKey(Function<L, K> leftKeyFunction, LeftSide<L> leftSide) {
			this.leftKeyFunction = leftKeyFunction;
			this.leftSide = leftSide;
		}

		public <R> RightSide<L, R, K> on(Stream<R> right) {
			return new RightSide<>(right, this);
		}
	}

	static class RightSide<L, R, K> {
		private final Stream<R> right;
		private final LeftKey<L, K> leftKey;

		public RightSide(Stream<R> right, LeftKey<L, K> leftKey) {
			this.right = right;
			this.leftKey = leftKey;
		}

		public RightKey<L, R, K> withKey(Function<R, K> rightKeyFunction) {
			return new RightKey<>(rightKeyFunction, this);
		}
	}

	static class RightKey<L, R, K> {
		private final Function<R, K> rightKeyFunction;
		private final RightSide<L, R, K> rightSide;

		public RightKey(Function<R, K> rightKeyFunction, RightSide<L, R, K> rightSide) {
			this.rightKeyFunction = rightKeyFunction;
			this.rightSide = rightSide;
		}

		public <Y> Stream<Y> combine(BiFunction<L, R, Y> combiner) {
            return  new Joiner<Y>(
                    createJoinStrategy(
							getIndexer(),
                            (l, rs) -> rs.map(r -> combiner.apply(l, r)),
							getJoinType()))
                    .doJoin();
		}


        public <Y> Stream<Y> group(BiFunction<L, Stream<R>, Y> grouper) {
            return new Joiner<Y>(
                    createJoinStrategy(
							getIndexer(),
							(l, rs) -> Stream.of(grouper.apply(l, rs)),
							getJoinType()))
                    .doJoin();
        }

		protected Indexer<L, R, K> getIndexer() {
			return new Indexer<>(
                    rightSide.leftKey.leftSide.left,
                    rightSide.leftKey.leftKeyFunction,
                    rightSide.right,
                    rightKeyFunction);
		}

        protected JoinType getJoinType() {
            return rightSide.leftKey.leftSide.joinType;
        }

        private <Y> JoinStrategy<Y> createJoinStrategy(Indexer<L, R, K> indexer, BiFunction<L, Stream<R>, Stream<Y>> grouper, JoinType joinType) {
            if (joinType.equals(JoinType.INNER)) {
                return new InnerEquiJoin<>(indexer, grouper);
            }

            if (joinType.equals(JoinType.LEFT_OUTER)) {
                return new LeftOuterJoin<>(indexer, grouper);
            }

            if (joinType.equals(JoinType.FULL_OUTER)) {
                return new LeftOuterJoin<>(indexer, grouper);
            }

            throw new UnsupportedOperationException();
        }
	}

}
