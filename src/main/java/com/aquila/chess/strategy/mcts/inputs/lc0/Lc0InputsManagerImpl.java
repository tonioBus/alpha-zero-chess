package com.aquila.chess.strategy.mcts.inputs.lc0;

import com.aquila.chess.AbstractGame;
import com.aquila.chess.strategy.mcts.inputs.InputRecord;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.utils.Coordinate;
import com.aquila.chess.utils.Utils;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.BoardUtils;
import com.chess.engine.classic.board.Move;
import com.chess.engine.classic.pieces.Piece;
import com.chess.engine.classic.player.Player;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class Lc0InputsManagerImpl extends InputsManager {

    static final int SIZE_POSITION = 13;

    // 5: Pawn:0, Bishop:1, Knight:2, Rook:3, Queen:4, King:5
    //
    // for 0..7
    //   [0-5] pieces for White
    //   [6-11] pieces for Black
    //   12: 1 or more repetition ??
    // end for
    // 104: white can castle queenside
    // 105: white can castle kingside
    // 106: black can castle queenside
    // 107: black can castle kingside
    // 108: 0 -> white turn  1 -> white turn
    // 109: repetitions whitout capture and pawn moves (50 moves rules)
    // 110: 0
    // 111: 1 -> edges detection
    public static final int FEATURES_PLANES = 138;

    public static final int PLANE_COLOR = 136;

    public static final int PAWN_INDEX = 0;
    public static final int KNIGHT_INDEX = 1;
    public static final int BISHOP_INDEX = 2;
    public static final int ROOK_INDEX = 3;
    public static final int QUEEN_INDEX = 4;
    public static final int KING_INDEX = 5;

    @Getter
    protected final CircularFifoQueue<Lc0Last8Inputs> lc0Last8Inputs = new CircularFifoQueue<>(8);

    public Lc0InputsManagerImpl() {
    }

    @Override
    public int getNbFeaturesPlanes() {
        return FEATURES_PLANES;
    }

    /**
     * @return
     */
    @Override
    public Lc0InputsFullNN createInputs(final InputRecord inputRecord) {
        final AbstractGame abstractGame = inputRecord.abstractGame();
        // final Move move = inputRecord.move();
        final Alliance moveColor = inputRecord.moveColor();
        final var inputs = new double[Lc0InputsManagerImpl.FEATURES_PLANES][BoardUtils.NUM_TILES_PER_ROW][BoardUtils.NUM_TILES_PER_ROW];
        //if (move != null && !move.isInitMove())
        // if we move, the moveColor will be the complementary of the player that just moved
        this.createInputs(
                inputs,
                new InputRecord(
                        abstractGame,
                        inputRecord.moves(),
                        inputRecord.move(),
                        moveColor)
        );
        return new Lc0InputsFullNN(inputs);
    }

    @Override
    public long hashCode(final InputRecord inputRecord) {
        String hashCodeString = getHashCodeString(inputRecord);
        long ret = Utils.hash(hashCodeString);
        log.debug("[{}] HASHCODE:{}\n{}", inputRecord.moveColor(), ret, hashCodeString);
        if (log.isDebugEnabled())
            log.warn("HASHCODE-1() -> [{}] MOVE:{} nbMaxBits:{} - {}", inputRecord.moveColor(), inputRecord.move(), Utils.nbMaxBits(ret), ret);
        return ret;
    }

    @Override
    public void startMCTSStep(final AbstractGame abstractGame) {
        int nbMoves = abstractGame.getMoves().size();
        if (nbMoves == 0 && this.lc0Last8Inputs.size() == 0) {
            final Board board = abstractGame.getLastBoard();
            final Lc0InputsOneNN inputs = this.createInputsForOnePosition(abstractGame.getLastBoard(), null, false);
            Move.InitMove initMove = switch (board.currentPlayer().getAlliance()) {
                case WHITE -> new Move.InitMove(board, Alliance.BLACK);
                case BLACK -> new Move.InitMove(board, Alliance.WHITE);
            };
            this.add(initMove, inputs);
        } else {
            int skipMoves = nbMoves < 8 ? 0 : nbMoves - 8;
            this.lc0Last8Inputs.clear();
            abstractGame.getMoves().stream().skip(skipMoves).forEach(currentMove -> {
                boolean isRepeat = isRepeatMove(currentMove);
                final Lc0InputsOneNN inputs = this.createInputsForOnePosition(currentMove.getBoard(), currentMove, isRepeat);
                log.debug("push input after init move:{}:\n{}", currentMove, inputs);
                this.add(currentMove, inputs);
            });
        }
    }

    @Override
    public InputsManager clone() {
        Lc0InputsManagerImpl lc0InputsManagerImpl = new Lc0InputsManagerImpl();
        doClone(lc0InputsManagerImpl);
        lc0InputsManagerImpl.lc0Last8Inputs.addAll(this.getLc0Last8Inputs());
        return lc0InputsManagerImpl;
    }

    @Override
    public void registerInput(final Board board, final Move move) {
        Lc0InputsOneNN inputs = this.createInputsForOnePosition(board, move, isRepeatMove(move));
        this.lc0Last8Inputs.add(new Lc0Last8Inputs(inputs, move, isRepeatMove(move)));
    }

    /**
     * <h1>Network Input</h1>
     * <p>
     * The input encoding follows the approach taken for AlphaZero.
     * The main difference is that the move count is no longer encoded — it is
     * technically not required since it’s just some superfluous extra-information. We should
     * also mention that Leela Chess Zero is an ongoing project, and naturally improvements
     * and code changes happen. The input format was subject to such changes
     * as well, for example to cope with chess variants such as Chess960 or Armageddon, or
     * simply to experiment with encodings. The encoding described here is
     * the classic encoding, referred to in source code as INPUT_CLASSICAL_112_PLANE.
     * For those who want to look up things in code, the relevant source files are
     * lc0/src/neural/encoder.cc and lc0/src/neural/encoder_test.cc.
     * The input consists of 112 planes of size 8 × 8. Information w.r.t. the placement
     * of pieces is encoded from the perspective of the player whose current turn it
     * is. Assume that we take that player’s perspective. The first plane encodes
     * the position of our own pawns. The second plane encodes the position of our
     * knights, then our bishops, rooks, queens and finally the king. Starting from
     * plane 6 we encode the position of the enemy’s pawns, then knights, bishops,
     * rooks, queens and the enemy’s king. Plane 12 is set to all ones if one or more
     * repetitions occurred.
     * These 12 planes are repeated to encode not only the current position, but also
     * the seven previous ones. Planes 104 to 107 are set to 1 if White can castle
     * queenside, White can castle kingside, Black can castle queenside and Black can
     * castle kingside (in that order). Plane 108 is set to all ones if it is Black’s turn and
     * to 0 otherwise. Plane 109 encodes the number of moves where no capture has
     * been made and no pawn has been moved, i.e. the 50 moves rule. Plane 110 used
     * to be a move counter, but is simply set to always 0 in current generations of Lc0.
     * Last, plane 111 is set to all ones. This is, as previously mentioned, to help the
     * network detect the edge of the board when using convolutional filters.
     * </p>
     * <ul>
     * <li>0 - 103: 8 times 13 -> 104 planes:
     * <ul>
     * <li> 0: positions for white pawn</li>
     * <li> 1: positions for white knights</li>
     * <li> 2: positions for white bishops</li>
     * <li> 3: positions for white rooks</li>
     * <li> 4: positions for white queens</li>
     * <li> 5: positions for white king</li>
     * <li> 6: positions for black pawn</li>
     * <li> 7: positions for black knights</li>
     * <li> 8: positions for black bishops</li>
     * <li> 9: positions for black rooks</li>
     * <li>10: positions for black queens</li>
     * <li>11: positions for black king</li>
     * <li>12: 1 if One or more repetition, what does it mean ?</li>
     * </ul>
     * </li>
     * <li>104: White can castle queenside (LONG)</li>
     * <li>105: White can castle kingside (SHORT)</li>
     * <li>106: Black can castle queenside (LONG)</li>
     * <li>107: Black can castle kingside (SHORT)</li>
     * <li>108: set to all ones if it is Black’s turn and to 0 otherwise</li>
     * <li>109: encodes the number of moves where no capture has been made and no pawn has been moved</li>
     * <li>110: move counter, seems 0 from now on</li>
     * <li>111: all ones, to help the network detect the edge of the board when using convolutional filters</li>
     * </ul>
     *
     * @param inputs
     */
    private void createInputs(final double[][][] inputs,
                              InputRecord inputRecord) {
        if (Utils.isDebuggerPresent()) log.debug("CREATE INPUT: {} {}", inputRecord.moveColor(), inputRecord.move());
        int destinationOffset = 0;
        final Board board = inputRecord.abstractGame().getBoard();
        CircularFifoQueue<Lc0Last8Inputs> tmp = new CircularFifoQueue<>(8);
        tmp.addAll(this.getLc0Last8Inputs());
        int size = this.getLc0Last8Inputs().size();
        Move lastMove = size == 0 ? null : this.getLc0Last8Inputs().get(size - 1).move();
        boolean addInputs = true;
        if (lastMove != null && inputRecord.move() != null) {
            if (lastMove.toString().equals(inputRecord.move().toString())) {
                addInputs = false;
            }
        }
        if (addInputs && inputRecord.move() != null) {
            boolean isRepeat = isRepeatMove(inputRecord.move());
            Lc0InputsOneNN lastInput1 = this.createInputsForOnePosition(board, inputRecord.move(), isRepeat);
            tmp.add(new Lc0Last8Inputs(lastInput1, inputRecord.move(), isRepeat));
        }
        List<Lc0Last8Inputs> listInputs = new ArrayList<>();
        listInputs.addAll(tmp);
        Collections.reverse(listInputs);
        for (Lc0Last8Inputs tmpLc0Last8Inputs : listInputs) {
            System.arraycopy(tmpLc0Last8Inputs.inputs().inputs(), 0, inputs, destinationOffset, SIZE_POSITION);
            if (Utils.isDebuggerPresent())
                log.debug("[{}] MOVE:{} COLOR:{}:\n{}", destinationOffset / SIZE_POSITION, tmpLc0Last8Inputs.move(), tmpLc0Last8Inputs.move().getAllegiance(), Lc0Utils.displayBoard(tmpLc0Last8Inputs.inputs().inputs(), 0));
            destinationOffset += SIZE_POSITION;
        }
        destinationOffset = 104; // 13 * 8
        for (Piece currentPiece : board.getAllPieces()) {
            // coordinate calculated from the point of view of the player
            Coordinate coordinate = new Coordinate(currentPiece);
            int currentPieceIndex = getPlanesIndex(currentPiece);
            Collection<Move> legalMoves = currentPiece.calculateLegalMoves(board);
            for (Move move : legalMoves) {
                // [104-115] MOVES Coordinate calculated from the point of view of the player
                Coordinate movesCoordinate = Coordinate.destinationCoordinate(move);
                inputs[destinationOffset + currentPieceIndex][movesCoordinate.getXInput()][movesCoordinate.getYInput()] = 1;
                // + 12 (6 + 6 planes) -> 104 + 12 = 116
                // [116-127] ATTACK
                if (move.isAttack()) {
                    Piece attackingPiece = move.getAttackedPiece();
                    Coordinate attackCoordinate = new Coordinate(attackingPiece);
                    inputs[destinationOffset + 12 + getPlanesIndex(attackingPiece)][attackCoordinate.getXInput()][attackCoordinate.getYInput()] = 1;
                }
                // + 24 (6 + 6 planes) -> 116 + 12 = 128
            }
            // [128-129] pawn moves until obstacles
            if (currentPiece.getPieceType() == Piece.PieceType.PAWN) {
                int offsetBlack = currentPiece.getPieceAllegiance() == Alliance.BLACK ? 1 : 0;
                int x = coordinate.getXInput();
                int y = coordinate.getYInput();
                Alliance color = currentPiece.getPieceAllegiance();
                for (int yIndex = y + 1; yIndex < BoardUtils.NUM_TILES_PER_ROW; yIndex++) {
                    if (board.getPiece(new Coordinate(x, yIndex, color).getBoardPosition()) != null) break;
                    inputs[destinationOffset + 24 + offsetBlack][x][yIndex] = 1;
                }
            }
            // [130-131] king liberty (only the opposite player king)
            // + 26 + 1  -> 130
            if (currentPiece.getPieceType() == Piece.PieceType.KING && inputRecord.moveColor() != currentPiece.getPieceAllegiance()) {
                Player player = switch (inputRecord.moveColor()) {
                    case WHITE -> board.whitePlayer();
                    case BLACK -> board.blackPlayer();
                };
                int offsetBlack = currentPiece.getPieceAllegiance() == Alliance.BLACK ? 1 : 0;
                for (Move move : legalMoves) {
                    Move.MoveStatus status = player.makeMove(move).getMoveStatus();
                    if (status == Move.MoveStatus.DONE) {
                        Coordinate coordinateKingMoves = Coordinate.destinationCoordinate(move);
                        inputs[destinationOffset + 26 + offsetBlack][coordinateKingMoves.getXInput()][coordinateKingMoves.getYInput()] = 1;
                    }
                }
            }
        }
        // + 28 (1 + 1 planes) -> 132
        destinationOffset = 132; // 104 + 12 + 12 + 2 + 2 = 104 + 28 -> 132
        List<Move> moveWhites = board.whitePlayer().getLegalMoves();
        Optional<Move> kingSideCastleWhite = moveWhites.stream().filter(m -> m instanceof Move.KingSideCastleMove).findFirst();
        Optional<Move> queenSideCastleWhite = moveWhites.stream().filter(m -> m instanceof Move.QueenSideCastleMove).findFirst();
        List<Move> moveBlacks = board.blackPlayer().getLegalMoves();
        Optional<Move> kingSideCastleBlack = moveBlacks.stream().filter(m -> m instanceof Move.KingSideCastleMove).findFirst();
        Optional<Move> queenSideCastleBlack = moveBlacks.stream().filter(m -> m instanceof Move.QueenSideCastleMove).findFirst();
        fill(inputs[destinationOffset], !queenSideCastleWhite.isEmpty() ? 1.0 : 0.0);
        fill(inputs[destinationOffset + 1], !kingSideCastleWhite.isEmpty() ? 1.0 : 0.0);
        fill(inputs[destinationOffset + 2], !queenSideCastleBlack.isEmpty() ? 1.0 : 0.0);
        fill(inputs[destinationOffset + 3], !kingSideCastleBlack.isEmpty() ? 1.0 : 0.0);
        fill(inputs[PLANE_COLOR], inputRecord.moveColor().isBlack() ? 1.0 : 0.0);
        fill(inputs[destinationOffset + 5], 1.0F);
        // 132 + 5 = 137
    }

    /**
     * @param board    - the board on which we apply the move
     * @param move     - the move to apply or null if nothing should be applied
     * @param isRepeat
     * @return the normalize board for 1 position using board and move. dimensions:
     * [13][NB_COL][NB_COL]
     */
    public Lc0InputsOneNN createInputsForOnePosition(Board board, final Move move, boolean isRepeat) {
        final var nbIn = new double[SIZE_POSITION][BoardUtils.NUM_TILES_PER_ROW][BoardUtils.NUM_TILES_PER_ROW];
        if (move != null && move.getDestinationCoordinate() != -1) {
            board = move.execute();
        }
        board.getAllPieces().stream().forEach(currentPiece -> {
            // coordinate calculated from the point of view of the player
            Coordinate coordinate = new Coordinate(currentPiece);
            int currentPieceIndex = getPlanesIndex(currentPiece);
            // Position 0 (6+6 planes)
            nbIn[currentPieceIndex][coordinate.getXInput()][coordinate.getYInput()] = 1;
        });
        // Repeat plan
        if (isRepeat)
            fill(nbIn[SIZE_POSITION - 1], 1.0);
        return new Lc0InputsOneNN(nbIn);
    }

    /**
     * @formatter:off <pre>
     * [0-6]: Pawn:0, Bishop:1, Knight:2, Rook:3, Queen:4, King:5
     * [0-6] pieces for White
     * [7-12] pieces for Black
     * </pre>
     * @formatter:on
     */
    private int getPlanesIndex(Piece piece) {
        int index = piece.getPieceAllegiance().isWhite() ? 0 : 6;
        if (piece.getPieceType() == Piece.PieceType.PAWN) return index + PAWN_INDEX;
        if (piece.getPieceType() == Piece.PieceType.BISHOP) return index + BISHOP_INDEX;
        if (piece.getPieceType() == Piece.PieceType.KNIGHT) return index + KNIGHT_INDEX;
        if (piece.getPieceType() == Piece.PieceType.ROOK) return index + ROOK_INDEX;
        if (piece.getPieceType() == Piece.PieceType.QUEEN) return index + QUEEN_INDEX;
        if (piece.getPieceType() == Piece.PieceType.KING) return index + KING_INDEX;
        return -100; // sure this will failed at least
    }

    private void fill(double[][] planes, double value) {
        if (value == 0.0) return;
        for (int i = 0; i < 8; i++) {
            Arrays.fill(planes[i], value);
        }
    }

    public String getHashCodeString(final InputRecord inputRecord) {
        final Move move = inputRecord.move();
        final Alliance moveColor = inputRecord.moveColor();
        Board board = inputRecord.abstractGame().getBoard();
        StringBuilder sb = new StringBuilder();
        List<Move> moves8inputs = this.lc0Last8Inputs.stream().map(in -> in.move()).collect(Collectors.toList());
        List<Boolean> repeats8inputs = this.lc0Last8Inputs.stream().map(in -> in.repeat()).collect(Collectors.toList());
        if (move != null && !move.isInitMove()) {
            if (notDuplicate(moves8inputs, move)) {
                try {
                    board = move.execute();
                    moves8inputs.add(move);
                } catch (Exception e) {
                    log.error("[{}] move:{}", move.getAllegiance(), move);
                    log.error("\n{}\n{}\n",
                            "##########################################",
                            board.toString()
                    );
                    throw e;
                }
            }
        }
        for (int position = 0; position < BoardUtils.NUM_TILES; position++) {
            Piece piece = board.getPiece(position);
            if (piece != null) {
                sb.append(String.format("%s=%d,", piece.getPieceType(), position));
            }
        }
        sb.append("\n");
        sb.append(moves8inputs.stream().map(m -> String.format("%s-%s", m.getAllegiance(), m)).collect(Collectors.joining(",")));
        sb.append("\n");
        sb.append(repeats8inputs.stream().map(repeat -> repeat ? "1" : "0").collect(Collectors.joining(",")));
        sb.append("\n");
        sb.append(moveColor);
        return sb.toString();
    }

    private boolean notDuplicate(List<Move> moves8inputs, Move move) {
        int size = moves8inputs.size();
        if (size > 0) {
            Move move1 = moves8inputs.get(size - 1);
            if (move1.toString().equals(move.toString())) {
//                log.error("duplicate in moves detected:{} MOVE to add:{}", moves8inputs.stream().map(m -> String.format("%s-%s", m.getAllegiance(), m))
//                        .collect(Collectors.joining(",")), move);
                return false;
            }
        }
        return true;
    }

    private void checkMoves(List<Move> moves8inputs) {
        int size = moves8inputs.size();
        if (size > 1) {
            Move move1 = moves8inputs.get(size - 1);
            Move move2 = moves8inputs.get(size - 2);
            if (move1.toString().equals(move2.toString())) {
                log.error("duplicate in moves detected:{}", moves8inputs.stream().map(move -> move.toString())
                        .collect(Collectors.joining(",")));
                throw new RuntimeException("duplicate moves found");
            }
        }
    }

    public void checkMoves(String moves) {
        final Set<String> elements = new HashSet<>();
        List<String> duplicates = Arrays.stream(moves.split(","))
                .filter(n -> !elements.add(n))
                .collect(Collectors.toList());
        if (duplicates.size() > 0) {
            log.info("checkMoves moves:{} ", moves);
            log.info("checkMoves duplicates moves:{} ", duplicates.stream()
                    .collect(Collectors.joining(",")));
            throw new RuntimeException("duplicate moves found");
        }
    }


    private void add(final Move move, final Lc0InputsOneNN lc0InputsOneNN) {
        this.lc0Last8Inputs.add(new Lc0Last8Inputs(lc0InputsOneNN, move, this.isRepeatMove(move)));
    }

}
